package org.kin.rsocket.broker;

import io.micrometer.core.instrument.Metrics;
import io.netty.util.collection.IntObjectHashMap;
import io.rsocket.ConnectionSetupPayload;
import io.rsocket.RSocket;
import io.rsocket.SocketAcceptor;
import io.rsocket.exceptions.ApplicationErrorException;
import io.rsocket.exceptions.RejectedSetupException;
import org.eclipse.collections.impl.map.mutable.UnifiedMap;
import org.eclipse.collections.impl.multimap.list.FastListMultimap;
import org.eclipse.collections.impl.multimap.set.UnifiedSetMultimap;
import org.kin.framework.utils.CollectionUtils;
import org.kin.framework.utils.MurmurHash3;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.auth.AuthenticationService;
import org.kin.rsocket.auth.RSocketAppPrincipal;
import org.kin.rsocket.broker.cluster.BrokerInfo;
import org.kin.rsocket.broker.cluster.RSocketBrokerManager;
import org.kin.rsocket.core.*;
import org.kin.rsocket.core.event.*;
import org.kin.rsocket.core.metadata.AppMetadata;
import org.kin.rsocket.core.metadata.BearerTokenMetadata;
import org.kin.rsocket.core.metadata.RSocketCompositeMetadata;
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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * broker 路由数据管理, 缓存service provider的信息
 * <p>
 * 1. 基于copy on write更新数据
 * 2. 因为加锁, 对服务注册性能有影响, 但不影响路由性能
 * 3. 同时注册服务数量较多数, 对瞬时内存(新生代)要求比较高, 因为每次注册需要全量复制缓存数据
 * 4. 为了追求更高性能, 不保证读立即可见性
 *
 * @author huangjianqin
 * @date 2021/3/30
 */
public final class RSocketServiceManager {
    private static final Logger log = LoggerFactory.getLogger(RSocketServiceManager.class);
    private final RSocketFilterChain rsocketFilterChain;
    private final Sinks.Many<String> notificationSink;
    /** 监听p2p服务实例变化 */
    private final Sinks.Many<String> p2pServiceNotificationSink;
    private final AuthenticationService authenticationService;
    private final RSocketBrokerManager brokerManager;
    private final RSocketServiceMeshInspector serviceMeshInspector;
    private final boolean authRequired;
    private final UpstreamCluster upstreamBrokers;
    private final ProviderRouter router;

    /** 修改数据需加锁 */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    /** key -> hash(app instance UUID), value -> 对应responder */
    private Map<Integer, BrokerResponder> instanceId2Responder = new UnifiedMap<>();
    /** key -> app instance UUID, value -> 对应responder */
    private Map<String, BrokerResponder> uuid2Responder = new UnifiedMap<>();
    /** key -> app name, value -> responder list */
    private FastListMultimap<String, BrokerResponder> appResponders = new FastListMultimap<>();
    /** key -> service id(gsv), value -> app instance UUID list */
    private UnifiedSetMultimap<String, Integer> p2pServiceConsumers = new UnifiedSetMultimap<>();

    /** key -> serviceId, value -> service info */
    private IntObjectHashMap<ServiceLocator> services = new IntObjectHashMap<>();
    /** key -> instanceId, value -> list(serviceId) */
    private UnifiedSetMultimap<Integer, Integer> instanceId2ServiceIds = new UnifiedSetMultimap<>();

    public RSocketServiceManager(RSocketFilterChain filterChain,
                                 Sinks.Many<String> notificationSink,
                                 AuthenticationService authenticationService,
                                 RSocketBrokerManager brokerManager,
                                 RSocketServiceMeshInspector serviceMeshInspector,
                                 boolean authRequired,
                                 UpstreamCluster upstreamBrokers,
                                 ProviderRouter router,
                                 Sinks.Many<String> p2pServiceNotificationSink) {
        this.rsocketFilterChain = filterChain;
        this.notificationSink = notificationSink;
        this.authenticationService = authenticationService;
        this.brokerManager = brokerManager;
        this.serviceMeshInspector = serviceMeshInspector;
        this.authRequired = authRequired;
        this.upstreamBrokers = upstreamBrokers;
        if (!brokerManager.isStandAlone()) {
            //broker集群变化通知downstream
            this.brokerManager.brokersChangedFlux().flatMap(this::broadcastClusterTopology).subscribe();
            this.brokerManager.brokersChangedFlux().subscribe(this::refreshClusterUpstreamBrokers);
            //先刷新一次
            refreshClusterUpstreamBrokers(brokerManager.all());
        }
        this.router = router;
        this.p2pServiceNotificationSink = p2pServiceNotificationSink;

        Metrics.globalRegistry.gauge(MetricsNames.BROKER_APPS_COUNT, this, manager -> manager.appResponders.size());
        Metrics.globalRegistry.gauge(MetricsNames.BROKER_SERVICE_PROVIDER_COUNT, this,
                manager -> manager.appResponders.valuesView()
                        .sumOfInt(responder -> (responder.isPublishServicesOnly() || responder.isConsumeAndPublishServices()) ? 0 : 1));
        Metrics.globalRegistry.gauge(MetricsNames.BROKER_SERVICE_COUNT, this, manager -> manager.services.size());
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
    private Mono<RSocket> acceptor(ConnectionSetupPayload setupPayload, RSocket requester) {
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
            } else if (compositeMetadata.contains(RSocketMimeType.BEARER_TOKEN)) {
                BearerTokenMetadata bearerTokenMetadata = compositeMetadata.getMetadata(RSocketMimeType.BEARER_TOKEN);
                credentials = new String(bearerTokenMetadata.getBearerToken());
                principal = authenticationService.auth(credentials);
            } else {
                // no jwt token supplied
                errorMsg = "Failed to accept the connection, please check app info and JWT token";
            }
            //validate application information
            if (principal != null && compositeMetadata.contains(RSocketMimeType.APPLICATION)) {
                AppMetadata temp = compositeMetadata.getMetadata(RSocketMimeType.APPLICATION);
                //App registration validation: app id: UUID and unique in server
                String appId = temp.getUuid();
                //validate appId data format
                if (StringUtils.isNotBlank(appId) && appId.length() >= 32) {
                    int instanceId = MurmurHash3.hash32(credentials + ":" + temp.getUuid());
                    temp.updateId(instanceId);
                    //application instance not connected
                    if (!containsInstanceId(instanceId)) {
                        appMetadata = temp;
                        appMetadata.updateConnectedAt(new Date());
                    } else {
                        // application connected already
                        errorMsg = "Connection created already, Please don't create multiple connections.";
                    }
                } else {
                    //没有uuid是否要拒绝连接
                    //illegal application id, appID should be UUID
                    errorMsg = String.format("'%s' is not legal application ID, please supply legal UUID as Application ID", appId == null ? "" : appId);
                }
            } else {
                errorMsg = "Can not found application metadata";
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
            return returnRejectedRSocket(errorMsg, requester);
        }
        //create responder
        try {
            RSocketServiceRequestHandler requestHandler = new RSocketServiceRequestHandler(setupPayload, appMetadata, principal,
                    this, serviceMeshInspector, upstreamBrokers, rsocketFilterChain);
            BrokerResponder responder = new BrokerResponder(compositeMetadata, appMetadata, requester, this, requestHandler);
            responder.onClose()
                    .doOnTerminate(() -> onResponderDisposed(responder))
                    .subscribeOn(Schedulers.parallel())
                    .subscribe();
            //handler registration notify
            registerResponder(responder);
            log.info(String.format("succeed to accept connection from application '%s'", appMetadata.getName()));
            return Mono.just(requestHandler);
        } catch (Exception e) {
            String formattedErrorMsg = String.format("failed to accept the connection: %s", e.getMessage());
            log.error(formattedErrorMsg, e);
            return returnRejectedRSocket(formattedErrorMsg, requester);
        }
    }

    /**
     * 注册downstream信息
     */
    private void registerResponder(BrokerResponder responder) {
        AppMetadata appMetadata = responder.getAppMetadata();

        Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            //copy on write
            Map<String, BrokerResponder> uuid2Responder = new UnifiedMap<>(this.uuid2Responder);
            uuid2Responder.put(appMetadata.getUuid(), responder);

            Integer instanceId = responder.getId();

            Map<Integer, BrokerResponder> instanceId2Responder = new UnifiedMap<>(this.instanceId2Responder);
            instanceId2Responder.put(instanceId, responder);

            FastListMultimap<String, BrokerResponder> appResponders = new FastListMultimap<>(this.appResponders);
            appResponders.put(appMetadata.getName(), responder);

            UnifiedSetMultimap<String, Integer> p2pServiceConsumers = new UnifiedSetMultimap<>(this.p2pServiceConsumers);
            for (String p2pService : appMetadata.getP2pServiceIds()) {
                p2pServiceConsumers.put(p2pService, instanceId);
                responder.fireCloudEvent(newServiceInstanceChangedCloudEvent(p2pService)).subscribe();
            }

            this.uuid2Responder = uuid2Responder;
            this.instanceId2Responder = instanceId2Responder;
            this.appResponders = appResponders;
            this.p2pServiceConsumers = p2pServiceConsumers;
        } finally {
            writeLock.unlock();
        }
        //广播事件
        RSocketAppContext.CLOUD_EVENT_SINK.tryEmitNext(AppStatusEvent.connected(appMetadata.getUuid()).toCloudEvent());
        if (!brokerManager.isStandAlone()) {
            //如果不是单节点, 则广播broker uris变化给downstream
            responder.fireCloudEvent(newBrokerClustersChangedCloudEvent(brokerManager.all(), appMetadata.getTopology())).subscribe();
        }
        notificationSink.tryEmitNext(String.format("app '%s' with ip '%s' online now!", appMetadata.getName(), appMetadata.getIp()));
    }

    /**
     * {@link BrokerResponder} disposed时触发的逻辑
     */
    private void onResponderDisposed(BrokerResponder responder) {
        AppMetadata appMetadata = responder.getAppMetadata();

        Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            //copy on write
            Map<String, BrokerResponder> uuid2Responder = new UnifiedMap<>(this.uuid2Responder);
            uuid2Responder.remove(responder.getUuid());

            Integer instanceId = responder.getId();

            Map<Integer, BrokerResponder> instanceId2Responder = new UnifiedMap<>(this.instanceId2Responder);
            instanceId2Responder.remove(instanceId);

            FastListMultimap<String, BrokerResponder> appResponders = new FastListMultimap<>(this.appResponders);
            appResponders.remove(appMetadata.getName(), responder);

            UnifiedSetMultimap<String, Integer> p2pServiceConsumers = new UnifiedSetMultimap<>(this.p2pServiceConsumers);
            for (String p2pService : appMetadata.getP2pServiceIds()) {
                p2pServiceConsumers.remove(p2pService, instanceId);
            }

            this.uuid2Responder = uuid2Responder;
            this.instanceId2Responder = instanceId2Responder;
            this.appResponders = appResponders;
            this.p2pServiceConsumers = p2pServiceConsumers;
        } finally {
            writeLock.unlock();
        }

        log.info(String.format("succeed to remove connection from application '%s'", appMetadata.getName()));
        RSocketAppContext.CLOUD_EVENT_SINK.tryEmitNext(AppStatusEvent.stopped(appMetadata.getUuid()).toCloudEvent());
        this.notificationSink.tryEmitNext(String.format("app '%s' with ip '%s' offline now!", appMetadata.getName(), appMetadata.getIp()));
    }

    /**
     * 获取所有app names
     */
    public Set<String> getAllAppNames() {
        return appResponders.keySet().toSet();
    }

    /**
     * 获取所有已注册的{@link BrokerResponder}
     */
    public Collection<BrokerResponder> getAllResponders() {
        return Collections.unmodifiableCollection(uuid2Responder.values());
    }

    /**
     * 根据app name 获取所有已注册的{@link BrokerResponder}
     */
    public Collection<BrokerResponder> getByAppName(String appName) {
        return Collections.unmodifiableCollection(appResponders.get(appName));
    }

    /**
     * 根据app uuid 获取已注册的{@link BrokerResponder}
     */
    public BrokerResponder getByUUID(String uuid) {
        return uuid2Responder.get(uuid);
    }

    /**
     * 根据app instanceId 获取已注册的{@link BrokerResponder}
     */
    public BrokerResponder getByInstanceId(int instanceId) {
        return instanceId2Responder.get(instanceId);
    }

    /**
     * 根据serviceId, 随机获取instanceId, 然后返回对应的已注册的{@link BrokerResponder}
     */
    public BrokerResponder getByServiceId(int serviceId) {
        Integer instanceId = router.route(serviceId);
        if (Objects.nonNull(instanceId)) {
            return instanceId2Responder.get(instanceId);
        } else {
            return null;
        }
    }

    /**
     * 向同一app name的所有app广播cloud event
     */
    public Mono<Void> broadcast(String appName, CloudEventData<?> cloudEvent) {
        if (appName.equals(Symbols.BROKER)) {
            return Flux.<BrokerResponder>create(s -> {
                for (BrokerResponder brokerResponder : instanceId2Responder.values()) {
                    s.next(brokerResponder);
                }
            }).flatMap(responder -> responder.fireCloudEvent(cloudEvent)).then();
        } else if (appResponders.containsKey(appName)) {
            return Flux.<BrokerResponder>create(s -> {
                for (BrokerResponder brokerResponder : appResponders.get(appName)) {
                    s.next(brokerResponder);
                }
            }).flatMap(responder -> responder.fireCloudEvent(cloudEvent)).then();
        } else {
            return Mono.error(new ApplicationErrorException("Application not found: appName=" + appName));
        }
    }

    /**
     * 向所有已注册的app广播cloud event
     */
    public Mono<Void> broadcast(CloudEventData<?> cloudEvent) {
        return Flux.<BrokerResponder>create(s -> {
            for (BrokerResponder brokerResponder : appResponders.valuesView()) {
                s.next(brokerResponder);
            }
        }).flatMap(responder -> responder.fireCloudEvent(cloudEvent)).then();
    }

    /**
     * 向指定uuid的app广播cloud event
     */
    public Mono<Void> send(String uuid, CloudEventData<?> cloudEvent) {
        BrokerResponder responder = uuid2Responder.get(uuid);
        if (responder != null) {
            return responder.fireCloudEvent(cloudEvent);
        } else {
            return Mono.error(new ApplicationErrorException("Application not found: app uuid=" + uuid));
        }
    }

    /**
     * 创建upstream broker变化的cloud event
     */
    private CloudEventData<UpstreamClusterChangedEvent> newBrokerClustersChangedCloudEvent(Collection<BrokerInfo> rsocketBrokerInfos, String topology) {
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
        upstreamClusterChangedEvent.setInterfaceName(Symbols.BROKER);
        upstreamClusterChangedEvent.setVersion("");
        upstreamClusterChangedEvent.setUris(uris);

        return CloudEventBuilder.builder(upstreamClusterChangedEvent)
                .dataSchema(URI.create("rsocket:" + UpstreamClusterChangedEvent.class.getName()))
                .build();
    }

    /**
     * 在集群模式下, 创建集群中与其余broker的连接
     */
    private void refreshClusterUpstreamBrokers(Collection<BrokerInfo> brokerInfos) {
        List<String> uris = new ArrayList<>(upstreamBrokers.getUris());
        uris.addAll(brokerInfos.stream().map(BrokerInfo::getUrl).collect(Collectors.toList()));

        upstreamBrokers.refreshUris(uris);
    }

    /**
     * 广播集群broker拓扑事件
     */
    private Flux<Void> broadcastClusterTopology(Collection<BrokerInfo> brokerInfos) {
        CloudEventData<UpstreamClusterChangedEvent> brokerClustersEvent = newBrokerClustersChangedCloudEvent(brokerInfos, Topologys.INTRANET);
        CloudEventData<UpstreamClusterChangedEvent> brokerClusterAliasesEvent = newBrokerClustersChangedCloudEvent(brokerInfos, Topologys.INTERNET);
        return Flux.fromIterable(getAllResponders()).flatMap(responder -> {
            String topology = responder.getAppMetadata().getTopology();
            Mono<Void> fireEvent;
            if (Topologys.INTERNET.equals(topology)) {
                fireEvent = responder.fireCloudEvent(brokerClusterAliasesEvent);
            } else {
                fireEvent = responder.fireCloudEvent(brokerClustersEvent);
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
    public void register(int instanceId, Set<ServiceLocator> services) {
        register(instanceId, 1, services);
    }

    /**
     * 注册app instance及其服务
     */
    public void register(int instanceId, int weight, Collection<ServiceLocator> serviceLocators) {
        Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            //copy on write
            UnifiedSetMultimap<Integer, Integer> instanceId2ServiceIds = new UnifiedSetMultimap<>(this.instanceId2ServiceIds);
            IntObjectHashMap<ServiceLocator> services = new IntObjectHashMap<>();
            services.putAll(this.services);

            for (ServiceLocator serviceLocator : serviceLocators) {
                int serviceId = serviceLocator.getId();
                String gsv = serviceLocator.getGsv();

                if (!instanceId2ServiceIds.get(instanceId).contains(serviceId)) {
                    instanceId2ServiceIds.put(instanceId, serviceId);
                    services.put(serviceId, serviceLocator);

                    //p2p service notification
                    if (p2pServiceConsumers.containsKey(gsv)) {
                        p2pServiceNotificationSink.tryEmitNext(gsv);
                    }
                }
            }

            this.instanceId2ServiceIds = instanceId2ServiceIds;
            this.services = services;

            router.onAppRegistered(instanceId, weight, serviceLocators);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 注销app instance及其服务
     */
    public void unregister(int instanceId) {
        Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            if (instanceId2ServiceIds.containsKey(instanceId)) {
                //copy on write
                UnifiedSetMultimap<Integer, Integer> instanceId2ServiceIds = new UnifiedSetMultimap<>(this.instanceId2ServiceIds);
                IntObjectHashMap<ServiceLocator> services = new IntObjectHashMap<>();
                services.putAll(this.services);

                Set<Integer> serviceIds = instanceId2ServiceIds.get(instanceId);
                for (Integer serviceId : serviceIds) {
                    //没有该serviceId对应instanceId了
                    ServiceLocator serviceLocator = services.remove(serviceId);
                    if (Objects.nonNull(serviceLocator)) {
                        String gsv = serviceLocator.getGsv();
                        if (p2pServiceConsumers.containsKey(gsv)) {
                            p2pServiceNotificationSink.tryEmitNext(gsv);
                        }
                    }
                }
                //移除该instanceId对应的所有serviceId
                instanceId2ServiceIds.removeAll(instanceId);

                this.instanceId2ServiceIds = instanceId2ServiceIds;
                this.services = services;

                router.onServiceUnregistered(instanceId, serviceIds);
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 注销app instance及其服务
     */
    public void unregister(int instanceId, int serviceId) {
        Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            if (instanceId2ServiceIds.containsKey(instanceId)) {
                //copy on write
                UnifiedSetMultimap<Integer, Integer> instanceId2ServiceIds = new UnifiedSetMultimap<>(this.instanceId2ServiceIds);
                IntObjectHashMap<ServiceLocator> services = new IntObjectHashMap<>();
                services.putAll(this.services);

                //没有该serviceId对应instanceId了
                services.remove(serviceId);

                //移除该instanceId上的serviceId
                instanceId2ServiceIds.remove(instanceId, serviceId);
                if (!instanceId2ServiceIds.containsKey(instanceId)) {
                    instanceId2ServiceIds.removeAll(instanceId);
                }

                this.instanceId2ServiceIds = instanceId2ServiceIds;
                this.services = services;

                router.onServiceUnregistered(instanceId, Collections.singleton(serviceId));
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 根据instanceId获取其所有serviceId
     */
    public Set<Integer> getServiceIds(int instanceId) {
        return Collections.unmodifiableSet(instanceId2ServiceIds.get(instanceId));
    }

    /**
     * instanceId是否已注册
     */
    public boolean containsInstanceId(int instanceId) {
        return instanceId2ServiceIds.containsKey(instanceId);
    }

    /**
     * serviceId是否已注册
     */
    public boolean containsServiceId(int serviceId) {
        return services.containsKey(serviceId);
    }

    /**
     * 根据serviceId获取其数据, 即{@link ServiceLocator}
     */
    public ServiceLocator getServiceLocator(int serviceId) {
        return services.get(serviceId);
    }

    /**
     * 根据serviceId获取其所有instanceId
     */
    public Collection<Integer> getAllInstanceIds(int serviceId) {
        return Collections.unmodifiableCollection(router.getAllInstanceIds(serviceId));
    }

    /**
     * 根据serviceId获取其所有已注册的{@link BrokerResponder}
     */
    public Collection<BrokerResponder> getAllByServiceId(int serviceId) {
        return getAllInstanceIds(serviceId).stream()
                .map(this::getByInstanceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 统计serviceId对应instanceId数量
     */
    public Integer countInstanceIds(int serviceId) {
        //有重复
        Collection<Integer> instanceIds = getAllInstanceIds(serviceId);
        if (CollectionUtils.isNonEmpty(instanceIds)) {
            return instanceIds.size();
        }
        return 0;
    }

    /**
     * 获取所有服务数据
     */
    public Collection<ServiceLocator> getAllServices() {
        return Collections.unmodifiableCollection(services.values());
    }

    /**
     * 获取p2p服务实例变化事件
     */
    private CloudEventData<ServiceInstanceChangedEvent> newServiceInstanceChangedCloudEvent(String gsv) {
        ServiceLocator serviceLocator = ServiceLocator.parse(gsv);
        Collection<Integer> instanceIdList = getAllInstanceIds(serviceLocator.getId());

        List<String> uris = new ArrayList<>(8);
        for (Integer instanceId : instanceIdList) {
            BrokerResponder responder = getByInstanceId(instanceId);
            if (Objects.isNull(responder)) {
                continue;
            }

            Map<Integer, String> rsocketPorts = responder.getAppMetadata().getRsocketPorts();
            if (rsocketPorts != null && !rsocketPorts.isEmpty()) {
                //组装成uri
                Map.Entry<Integer, String> entry = rsocketPorts.entrySet().stream().findFirst().get();
                String uri = entry.getValue() + "://" + responder.getAppMetadata().getIp() + ":" + entry.getKey();
                uris.add(uri);
            }
        }
        ServiceInstanceChangedEvent event = ServiceInstanceChangedEvent.of(
                serviceLocator.getGroup(),
                serviceLocator.getService(),
                serviceLocator.getVersion(),
                uris);
        return event.toCloudEvent();
    }

    /**
     * 获取指定rsocket服务的p2p consumer端 instance id list
     */
    private Set<Integer> getP2pServiceConsumerInstanceIds(String gsv) {
        return p2pServiceConsumers.get(gsv);
    }

    /**
     * 广播rsocket服务实例变化
     */
    private Flux<Void> broadcastServiceInstanceChanged(String gsv) {
        CloudEventData<ServiceInstanceChangedEvent> cloudEvent = newServiceInstanceChangedCloudEvent(gsv);
        return Flux.fromIterable(getP2pServiceConsumerInstanceIds(gsv))
                .flatMap(instanceId -> {
                    BrokerResponder responder = getByInstanceId(instanceId);
                    if (Objects.nonNull(responder)) {
                        return responder.fireCloudEvent(cloudEvent);
                    } else {
                        return Mono.empty();
                    }
                }).subscribeOn(Schedulers.parallel());
    }

    /**
     * 更新指定app应用开启的p2p服务gsv
     */
    public void updateP2pServiceConsumers(String appId, Set<String> p2pServiceIds) {
        BrokerResponder responder = getByUUID(appId);
        if (responder != null) {
            AppMetadata appMetadata = responder.getAppMetadata();
            Integer instanceId = responder.getId();
            appMetadata.updateP2pServiceIds(p2pServiceIds);

            Lock writeLock = lock.writeLock();
            writeLock.lock();
            try {
                //copy on write
                UnifiedSetMultimap<String, Integer> p2pServiceConsumers = new UnifiedSetMultimap<>(this.p2pServiceConsumers);

                for (String p2pService : appMetadata.getP2pServiceIds()) {
                    p2pServiceConsumers.put(p2pService, instanceId);
                    responder.fireCloudEvent(newServiceInstanceChangedCloudEvent(p2pService)).subscribe();
                }

                this.p2pServiceConsumers = p2pServiceConsumers;
            } finally {
                writeLock.unlock();
            }
        }
    }
}

