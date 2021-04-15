package org.kin.rsocket.broker;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import io.netty.util.collection.IntObjectHashMap;
import io.rsocket.ConnectionSetupPayload;
import io.rsocket.RSocket;
import io.rsocket.SocketAcceptor;
import io.rsocket.exceptions.ApplicationErrorException;
import io.rsocket.exceptions.RejectedSetupException;
import org.kin.rsocket.auth.AuthenticationService;
import org.kin.rsocket.auth.RSocketAppPrincipal;
import org.kin.rsocket.broker.cluster.BrokerInfo;
import org.kin.rsocket.broker.cluster.BrokerManager;
import org.kin.rsocket.core.*;
import org.kin.rsocket.core.domain.AppStatus;
import org.kin.rsocket.core.event.AppStatusEvent;
import org.kin.rsocket.core.event.CloudEventBuilder;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.UpstreamClusterChangedEvent;
import org.kin.rsocket.core.metadata.AppMetadata;
import org.kin.rsocket.core.metadata.BearerTokenMetadata;
import org.kin.rsocket.core.metadata.RSocketCompositeMetadata;
import org.kin.rsocket.core.utils.MurmurHash3;
import org.kin.rsocket.core.utils.Symbols;
import org.kin.rsocket.core.utils.Topologys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * broker 路由器, 负责根据downstream请求元数据将请求路由到目标upstream
 * todo 看看需不需要定时清掉空闲连接的downstream
 *
 * @author huangjianqin
 * @date 2021/3/30
 */
public final class ServiceManager {
    private static final Logger log = LoggerFactory.getLogger(ServiceManager.class);
    private final RSocketFilterChain rsocketFilterChain;
    private final ReactiveServiceRegistry serviceRegistry;
    private final Sinks.Many<CloudEventData<?>> cloudEventSink;
    private final Sinks.Many<String> notificationSink;
    private final AuthenticationService authenticationService;
    private final BrokerManager brokerManager;
    private final ServiceMeshInspector serviceMeshInspector;
    private final boolean authRequired;
    private final UpstreamCluster upstreamBrokers;

    /** key -> hash(app instance UUID), value -> 对应responder */
    private final Map<Integer, ServiceResponder> instanceId2Responder = new ConcurrentHashMap<>();
    /** key -> app instance UUID, value -> 对应responder */
    private final Map<String, ServiceResponder> uuid2Responder = new ConcurrentHashMap<>();
    /** key -> app name, value -> responder list */
    private final ListMultimap<String, ServiceResponder> appResponders = MultimapBuilder.hashKeys().arrayListValues().build();

    /** key -> serviceId, value -> list(instanceId, 也就是hash(app uuid) (会重复的, 数量=权重, 用于随机获取对应的instanceId)) */
    private final ListMultimap<Integer, Integer> serviceId2InstanceIds = MultimapBuilder.hashKeys().arrayListValues().build();
    /** key -> serviceId, value -> service info */
    private final IntObjectHashMap<ServiceLocator> services = new IntObjectHashMap<>();
    /** key -> instanceId, value -> list(serviceId) */
    private final SetMultimap<Integer, Integer> instanceId2ServiceIds = MultimapBuilder.hashKeys().linkedHashSetValues().build();

    public ServiceManager(ReactiveServiceRegistry serviceRegistry,
                          RSocketFilterChain filterChain,
                          Sinks.Many<CloudEventData<?>> cloudEventSink,
                          Sinks.Many<String> notificationSink,
                          AuthenticationService authenticationService,
                          BrokerManager brokerManager,
                          ServiceMeshInspector serviceMeshInspector,
                          boolean authRequired,
                          UpstreamCluster upstreamBrokers) {
        this.serviceRegistry = serviceRegistry;
        this.rsocketFilterChain = filterChain;
        this.cloudEventSink = cloudEventSink;
        this.notificationSink = notificationSink;
        this.authenticationService = authenticationService;
        this.brokerManager = brokerManager;
        this.serviceMeshInspector = serviceMeshInspector;
        this.authRequired = authRequired;
        this.upstreamBrokers = upstreamBrokers;
        if (!brokerManager.isStandAlone()) {
            this.brokerManager.brokersChangedFlux().flatMap(this::broadcastClusterTopology).subscribe();
        }
    }

    /**
     * 返回broker端口 responder acceptor
     */
    public SocketAcceptor acceptor() {
        return this::acceptor;
    }

    /**
     * service responder acceptor逻辑
     */
    private Mono<RSocket> acceptor(ConnectionSetupPayload setupPayload, RSocket requesterSocket) {
        //parse setup payload
        RSocketCompositeMetadata compositeMetadata = null;
        AppMetadata appMetadata = null;
        String credentials = "";
        RSocketAppPrincipal principal = null;
        String errorMsg = null;
        try {
            compositeMetadata = RSocketCompositeMetadata.of(setupPayload.metadata());
            if (!authRequired) {
                //authentication not required
                principal = RSocketAppPrincipal.DEFAULT;
                credentials = UUID.randomUUID().toString();
            } else if (compositeMetadata.contains(RSocketMimeType.BearerToken)) {
                BearerTokenMetadata bearerTokenMetadata = compositeMetadata.getMetadata(RSocketMimeType.BearerToken);
                credentials = new String(bearerTokenMetadata.getBearerToken());
                principal = authenticationService.auth(credentials);
            } else {
                // no jwt token supplied
                errorMsg = "Failed to accept the connection, please check app info and JWT token";
            }
            //validate application information
            if (principal != null && compositeMetadata.contains(RSocketMimeType.Application)) {
                AppMetadata temp = compositeMetadata.getMetadata(RSocketMimeType.Application);
                //App registration validation: app id: UUID and unique in server
                if (temp.getUuid() == null || temp.getUuid().isEmpty()) {
                    temp.setUuid(UUID.randomUUID().toString());
                }
                String appId = temp.getUuid();
                //validate appId data format
                if (appId != null && appId.length() >= 32) {
                    Integer instanceId = MurmurHash3.hash32(credentials + ":" + temp.getUuid());
                    temp.setId(instanceId);
                    //application instance not connected
                    if (!containsInstanceId(instanceId)) {
                        appMetadata = temp;
                        appMetadata.setConnectedAt(new Date());
                    } else {
                        // application connected already
                        errorMsg = "Connection created already, Please don't create multiple connections.";
                    }
                } else {
                    //illegal application id, appID should be UUID
                    errorMsg = String.format("'%s' is not legal application ID, please supply legal UUID as Application ID", appId == null ? "" : appId);
                }
            }
            if (errorMsg == null) {
                //Security authentication
                if (appMetadata != null) {
                    appMetadata.addMetadata("_orgs", String.join(",", principal.getOrganizations()));
                    appMetadata.addMetadata("_roles", String.join(",", principal.getRoles()));
                    appMetadata.addMetadata("_serviceAccounts", String.join(",", principal.getServiceAccounts()));
                } else {
                    errorMsg = "Please supply message/x.rsocket.application+json metadata in setup payload";
                }
            }
        } catch (Exception e) {
            log.error("Error to parse setup payload", e);
            errorMsg = String.format("Failed to parse composite metadata: %s", e.getMessage());
        }
        //validate connection legal or not
        if (principal == null) {
            errorMsg = "Failed to accept the connection, please check app info and JWT token";
        }
        if (errorMsg != null) {
            return returnRejectedRSocket(errorMsg, requesterSocket);
        }
        //create responder
        try {
            ServiceResponder responder = new ServiceResponder(setupPayload, compositeMetadata, appMetadata, principal,
                    requesterSocket, cloudEventSink, this,
                    serviceMeshInspector, upstreamBrokers, rsocketFilterChain, serviceRegistry);
            responder.onClose()
                    .doOnTerminate(() -> onResponderDisposed(responder))
                    .subscribeOn(Schedulers.parallel()).subscribe();
            //handler registration notify
            registerResponder(responder);
            log.info(String.format("Succeed to accept connection from '%s'", appMetadata.getName()));
            return Mono.just(responder);
        } catch (Exception e) {
            String formatedErrorMsg = String.format("Failed to accept the connection: %s", e.getMessage());
            log.error(formatedErrorMsg, e);
            return returnRejectedRSocket(formatedErrorMsg, requesterSocket);
        }
    }

    /**
     * 注册downstream信息
     */
    private void registerResponder(ServiceResponder responder) {
        AppMetadata appMetadata = responder.getAppMetadata();
        uuid2Responder.put(appMetadata.getUuid(), responder);
        instanceId2Responder.put(responder.getId(), responder);
        appResponders.put(appMetadata.getName(), responder);
        //广播事件
        cloudEventSink.tryEmitNext(newAppStatusEvent(appMetadata, AppStatus.CONNECTED));
        if (!brokerManager.isStandAlone()) {
            //如果不是单节点, 则广播broker uris变化给downstream
            responder.fireCloudEventToPeer(newBrokerClustersChangedEvent(brokerManager.all(), appMetadata.getTopology())).subscribe();
        }
        notificationSink.tryEmitNext(String.format("App '%s' with IP '%s' Online now!", appMetadata.getName(), appMetadata.getIp()));
    }

    /**
     * {@link ServiceResponder} disposed时触发的逻辑
     */
    private void onResponderDisposed(ServiceResponder responder) {
        AppMetadata appMetadata = responder.getAppMetadata();
        uuid2Responder.remove(responder.getUuid());
        instanceId2Responder.remove(responder.getId());
        appResponders.remove(appMetadata.getName(), responder);
        log.info("Succeed to remove broker handler");
        cloudEventSink.tryEmitNext(newAppStatusEvent(appMetadata, AppStatus.STOPPED));
        this.notificationSink.tryEmitNext(String.format("App '%s' with IP '%s' Offline now!", appMetadata.getName(), appMetadata.getIp()));
    }

    /**
     * 获取所有app names
     */
    public Collection<String> getAllAppNames() {
        return appResponders.keySet();
    }

    /**
     * 获取所有已注册的{@link ServiceResponder}
     */
    public Collection<ServiceResponder> getAllResponders() {
        return uuid2Responder.values();
    }

    /**
     * 根据app name 获取所有已注册的{@link ServiceResponder}
     */
    public Collection<ServiceResponder> getByAppName(String appName) {
        return appResponders.get(appName);
    }

    /**
     * 根据app uuid 获取已注册的{@link ServiceResponder}
     */
    public ServiceResponder getByUUID(String uuid) {
        return uuid2Responder.get(uuid);
    }

    /**
     * 根据app instanceId 获取所有已注册的{@link ServiceResponder}
     */
    public ServiceResponder getByInstanceId(Integer instanceId) {
        return instanceId2Responder.get(instanceId);
    }

    /**
     * 向同一app name的所有app广播cloud event
     */
    public Mono<Void> broadcast(String appName, CloudEventData<?> cloudEvent) {
        if (appName.equals(Symbols.BROKER)) {
            return Flux.fromIterable(instanceId2Responder.values())
                    .flatMap(handler -> handler.fireCloudEventToPeer(cloudEvent))
                    .then();
        } else if (appResponders.containsKey(appName)) {
            return Flux.fromIterable(appResponders.get(appName))
                    .flatMap(handler -> handler.fireCloudEventToPeer(cloudEvent))
                    .then();
        } else {
            return Mono.error(new ApplicationErrorException("Application not found: appName=" + appName));
        }
    }

    /**
     * 向所有已注册的app广播cloud event
     */
    public Mono<Void> broadcast(CloudEventData<?> cloudEvent) {
        return Flux.fromIterable(appResponders.keySet())
                .flatMap(name -> Flux.fromIterable(appResponders.get(name)))
                .flatMap(handler -> handler.fireCloudEventToPeer(cloudEvent))
                .then();
    }

    /**
     * 向指定uuid的app广播cloud event
     */
    public Mono<Void> send(String uuid, CloudEventData<?> cloudEvent) {
        ServiceResponder responder = uuid2Responder.get(uuid);
        if (responder != null) {
            return responder.fireCloudEvent(cloudEvent);
        } else {
            return Mono.error(new ApplicationErrorException("Application not found: app uuid=" + uuid));
        }
    }

    /**
     * 创建{@link AppStatusEvent} cloud event
     */
    private CloudEventData<AppStatusEvent> newAppStatusEvent(AppMetadata appMetadata, AppStatus status) {
        return CloudEventBuilder
                .builder(AppStatusEvent.of(appMetadata.getUuid(), status))
                .build();
    }

    /**
     * 创建upstream broker变化的cloud event
     */
    private CloudEventData<UpstreamClusterChangedEvent> newBrokerClustersChangedEvent(Collection<BrokerInfo> rsocketBrokerInfos, String topology) {
        List<String> uris;
        if (Topologys.INTERNET.equals(topology)) {
            uris = rsocketBrokerInfos.stream()
                    .filter(rsocketBroker -> rsocketBroker.isActive() && rsocketBroker.getExternalDomain() != null)
                    .map(BrokerInfo::getAliasUrl)
                    .collect(Collectors.toList());
        } else {
            uris = rsocketBrokerInfos.stream()
                    .filter(BrokerInfo::isActive)
                    .map(BrokerInfo::getUrl)
                    .collect(Collectors.toList());
        }

        UpstreamClusterChangedEvent upstreamClusterChangedEvent = new UpstreamClusterChangedEvent();
        upstreamClusterChangedEvent.setGroup("");
        //仅仅广播broker
        upstreamClusterChangedEvent.setInterfaceName(Symbols.BROKER);
        upstreamClusterChangedEvent.setVersion("");
        upstreamClusterChangedEvent.setUris(uris);

        return CloudEventBuilder.builder(upstreamClusterChangedEvent)
                .withDataschema(URI.create("rsocket:" + UpstreamClusterChangedEvent.class.getName()))
                .withSource(RSocketAppContext.SOURCE)
                .build();
    }

    /**
     *
     */
    private Flux<Void> broadcastClusterTopology(Collection<BrokerInfo> brokerInfos) {
        CloudEventData<UpstreamClusterChangedEvent> brokerClustersEvent = newBrokerClustersChangedEvent(brokerInfos, Topologys.INTRANET);
        CloudEventData<UpstreamClusterChangedEvent> brokerClusterAliasesEvent = newBrokerClustersChangedEvent(brokerInfos, Topologys.INTERNET);
        return Flux.fromIterable(getAllResponders()).flatMap(responder -> {
            String topology = responder.getAppMetadata().getTopology();
            Mono<Void> fireEvent;
            if (Topologys.INTERNET.equals(topology)) {
                //todo
                // add defaultUri for internet access for IoT devices
                // RSocketBroker defaultBroker = rsocketBrokerManager.findConsistentBroker(responder.getUuid());
                fireEvent = responder.fireCloudEventToPeer(brokerClusterAliasesEvent);
            } else {
                fireEvent = responder.fireCloudEventToPeer(brokerClustersEvent);
            }
            if (responder.isPublishServicesOnly()) {
                return fireEvent;
            } else if (responder.isConsumeAndPublishServices()) {
                return fireEvent.delayElement(Duration.ofSeconds(15));
            } else {
                //consume services only
                return fireEvent.delayElement(Duration.ofSeconds(30));
            }
        });
    }

    /**
     * return rejected Rsocket with dispose logic
     */
    private Mono<RSocket> returnRejectedRSocket(String errorMsg, RSocket requesterSocket) {
        return Mono.<RSocket>error(new RejectedSetupException(errorMsg)).doFinally((signalType -> {
            if (!requesterSocket.isDisposed()) {
                requesterSocket.dispose();
            }
        }));
    }

    /**
     * 注册app instance及其服务
     */
    public void register(Integer instanceId, Set<ServiceLocator> services) {
        register(instanceId, 1, services);
    }

    /**
     * 注册app instance及其服务
     */
    public void register(Integer instanceId, int powerUnit, Set<ServiceLocator> services) {
        //todo notification for global service
        for (ServiceLocator serviceLocator : services) {
            int serviceId = serviceLocator.getId();
            if (!instanceId2ServiceIds.get(instanceId).contains(serviceId)) {
                instanceId2ServiceIds.put(instanceId, serviceId);
                for (int i = 0; i < powerUnit; i++) {
                    //put n个
                    serviceId2InstanceIds.put(serviceId, instanceId);
                }
                this.services.put(serviceId, serviceLocator);
            }
        }
    }

    /**
     * 注销app instance及其服务
     */
    public void unregister(Integer instanceId) {
        if (instanceId2ServiceIds.containsKey(instanceId)) {
            for (Integer serviceId : instanceId2ServiceIds.get(instanceId)) {
                //移除所有相同的instanceId
                while (serviceId2InstanceIds.remove(serviceId, instanceId)) {
                    //do nothing
                }
                if (!serviceId2InstanceIds.containsKey(serviceId)) {
                    //没有该serviceId对应instanceId了
                    services.remove(serviceId);
                }
            }
            //移除该instanceId对应的所有serviceId
            instanceId2ServiceIds.removeAll(instanceId);
        }
    }

    /**
     * 注销app instance及其服务
     */
    public void unregister(Integer instanceId, Integer serviceId) {
        if (instanceId2ServiceIds.containsKey(instanceId)) {
            //移除所有相同的instanceId
            while (serviceId2InstanceIds.remove(serviceId, instanceId)) {
                //do nothing
            }
            if (!serviceId2InstanceIds.containsKey(serviceId)) {
                //没有该serviceId对应instanceId了
                services.remove(serviceId);
            }
            //移除该instanceId上的serviceId
            instanceId2ServiceIds.remove(instanceId, serviceId);
            if (!instanceId2ServiceIds.containsKey(instanceId)) {
                instanceId2ServiceIds.removeAll(instanceId);
            }
        }
    }

    /**
     * 根据instanceId获取其所有serviceId
     */
    public Set<Integer> getServiceIds(Integer instanceId) {
        return instanceId2ServiceIds.get(instanceId);
    }

    /**
     * instanceId是否已注册
     */
    public boolean containsInstanceId(Integer instanceId) {
        return instanceId2ServiceIds.containsKey(instanceId);
    }

    /**
     * serviceId是否已注册
     */
    public boolean containsServiceId(Integer serviceId) {
        return serviceId2InstanceIds.containsKey(serviceId);
    }

    /**
     * 根据serviceId获取其数据, 即{@link ServiceLocator}
     */
    public ServiceLocator getServiceLocator(Integer serviceId) {
        return services.get(serviceId);
    }

    /**
     * 根据serviceId获取对应instanceId
     */
    public Integer getInstanceId(Integer serviceId) {
        List<Integer> instanceIds = serviceId2InstanceIds.get(serviceId);
        int handlerCount = instanceIds.size();
        if (handlerCount > 1) {
            try {
                return instanceIds.get(ThreadLocalRandom.current().nextInt(handlerCount));
            } catch (Exception e) {
                return instanceIds.get(0);
            }
        } else if (handlerCount == 1) {
            return instanceIds.get(0);
        } else {
            return null;
        }
    }

    /**
     * 根据serviceId获取其所有instanceId
     */
    public Collection<Integer> getAllInstanceIds(Integer serviceId) {
        return serviceId2InstanceIds.get(serviceId);

    }

    /**
     * 统计serviceId对应instanceId数量
     */
    public Integer countInstanceIds(Integer serviceId) {
        //有重复
        List<Integer> instanceIds = serviceId2InstanceIds.get(serviceId);
        return new HashSet<>(instanceIds).size();
    }

    /**
     * 统计serviceId对应instanceId数量
     */
    public Integer countInstanceIds(String serviceName) {
        return countInstanceIds(MurmurHash3.hash32(serviceName));
    }

    /**
     * 获取所有服务数据
     */
    public Collection<ServiceLocator> getAllServices() {
        return services.values();
    }

    /**
     * 已注册服务数量
     */
    public int countServices() {
        return services.keySet().size();
    }
}

