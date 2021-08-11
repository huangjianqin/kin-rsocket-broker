package org.kin.rsocket.broker;

import io.rsocket.DuplexConnection;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import org.kin.framework.utils.CollectionUtils;
import org.kin.rsocket.auth.RSocketAppPrincipal;
import org.kin.rsocket.core.RSocketMimeType;
import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.domain.AppStatus;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.CloudEventRSocket;
import org.kin.rsocket.core.event.CloudEventSupport;
import org.kin.rsocket.core.metadata.AppMetadata;
import org.kin.rsocket.core.metadata.RSocketCompositeMetadata;
import org.kin.rsocket.core.metadata.RSocketServiceRegistryMetadata;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ReflectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.*;

/**
 * service <- broker
 * broker请求service
 *
 * @author huangjianqin
 * @date 2021/3/30
 */
public final class BrokerResponder implements CloudEventRSocket {
    private static final Logger log = LoggerFactory.getLogger(BrokerResponder.class);
    /** app metadata */
    private final AppMetadata appMetadata;
    /**
     * peer service app的id uuid ip以及metedata字段hashcode
     * 目前用于根据endpoint 快速路由
     * 不可变
     */
    private final Set<Integer> appTagsHashCodeSet;
    /** peer requester RSocket */
    private final RSocket requester;
    private final RSocketServiceManager serviceManager;
    private final Mono<Void> comboOnClose;
    /** remote requester ip */
    private final String remoteIp;
    /** peer RSocket暴露的服务 */
    private Set<ServiceLocator> peerServices;
    /** app status */
    private AppStatus appStatus = AppStatus.CONNECTED;
    /** requester请求处理handler */
    private final RSocketServiceRequestHandler requestHandler;

    public BrokerResponder(RSocketCompositeMetadata compositeMetadata,
                           AppMetadata appMetadata,
                           RSocket requester,
                           RSocketServiceManager serviceManager,
                           RSocketServiceRequestHandler requestHandler) {
        this.appMetadata = appMetadata;
        //app tags hashcode set
        Set<Integer> appTagsHashCodeSet = new HashSet<>(4);
        appTagsHashCodeSet.add(("id:" + appMetadata.getId()).hashCode());
        appTagsHashCodeSet.add(("uuid:" + appMetadata.getUuid()).hashCode());

        if (appMetadata.getIp() != null && !appMetadata.getIp().isEmpty()) {
            appTagsHashCodeSet.add(("ip:" + this.appMetadata.getIp()).hashCode());
        }

        if (appMetadata.getMetadata() != null) {
            for (Map.Entry<String, String> entry : appMetadata.getMetadata().entrySet()) {
                appTagsHashCodeSet.add((entry.getKey() + ":" + entry.getValue()).hashCode());
            }
        }
        this.appTagsHashCodeSet = Collections.unmodifiableSet(appTagsHashCodeSet);

        this.requester = requester;
        this.serviceManager = serviceManager;

        //publish services metadata
        this.peerServices = new HashSet<>();
        if (compositeMetadata.contains(RSocketMimeType.SERVICE_REGISTRY)) {
            RSocketServiceRegistryMetadata serviceRegistryMetadata = compositeMetadata.getMetadata(RSocketMimeType.SERVICE_REGISTRY);
            if (CollectionUtils.isNonEmpty(serviceRegistryMetadata.getPublished())) {
                peerServices.addAll(serviceRegistryMetadata.getPublished());
                publishServices();
            }
        }


        //remote ip
        this.remoteIp = getRemoteAddress(requester);
        this.requestHandler = requestHandler;
        //new comboOnClose
        this.comboOnClose = Mono.firstWithSignal(requestHandler.onClose(), requester.onClose());
        this.comboOnClose.doOnTerminate(this::hideServices).subscribeOn(Schedulers.parallel()).subscribe();
    }

    @Override
    public Mono<Void> fireAndForget(Payload payload) {
        return requester.fireAndForget(payload);
    }

    @Override
    public Mono<Payload> requestResponse(Payload payload) {
        return requester.requestResponse(payload);
    }

    @Override
    public Flux<Payload> requestStream(Payload payload) {
        return requester.requestStream(payload);
    }

    @Override
    public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
        return requester.requestChannel(payloads);
    }

    @Override
    public Mono<Void> metadataPush(Payload payload) {
        return requester.metadataPush(payload);
    }

    @Override
    public Mono<Void> fireCloudEvent(CloudEventData<?> cloudEvent) {
        try {
            Payload payload = CloudEventSupport.cloudEvent2Payload(cloudEvent);
            return metadataPush(payload);
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    @Override
    public Mono<Void> fireCloudEvent(String cloudEventJson) {
        try {
            Payload payload = CloudEventSupport.cloudEvent2Payload(cloudEventJson);
            return metadataPush(payload);
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    @Override
    public Mono<Void> onClose() {
        return this.comboOnClose;
    }

    /**
     * 暴露peer rsocket的服务, 并修改该app的服务状态
     */
    public void publishServices() {
        if (CollectionUtils.isNonEmpty(this.peerServices)) {
            Set<Integer> serviceIds = serviceManager.getServiceIds(appMetadata.getId());
            if (serviceIds.isEmpty()) {
                this.serviceManager.register(appMetadata.getId(), appMetadata.getWeight(), peerServices);
                this.appStatus = AppStatus.SERVING;
            }
        }
    }

    /** 注册指定服务 */
    public void registerServices(Collection<ServiceLocator> services) {
        this.peerServices.addAll(services);
        this.serviceManager.register(appMetadata.getId(), appMetadata.getWeight(), services);
        this.appStatus = AppStatus.SERVING;
    }

    /** 注册指定服务 */
    public void registerServices(ServiceLocator... services) {
        registerServices(Arrays.asList(services));
    }

    /**
     * 隐藏peer rsocket的服务, 并修改该app的服务状态
     */
    public void hideServices() {
        serviceManager.unregister(appMetadata.getId());
        this.appStatus = AppStatus.DOWN;
    }

    /** 注销指定服务 */
    public void unregisterServices(Collection<ServiceLocator> services) {
        if (this.peerServices != null && !this.peerServices.isEmpty()) {
            this.peerServices.removeAll(services);
        }
        for (ServiceLocator service : services) {
            this.serviceManager.unregister(appMetadata.getId(), service.getId());
        }
    }

    /** 注销指定服务 */
    public void unregisterServices(ServiceLocator... services) {
        unregisterServices(Arrays.asList(services));
    }

    /**
     * @return requester publish services only
     */
    public boolean isPublishServicesOnly() {
        return requestHandler.everConsumed() && CollectionUtils.isNonEmpty(peerServices);
    }

    /**
     * @return requester consume and publish services
     */
    public boolean isConsumeAndPublishServices() {
        return requestHandler.everConsumed() && CollectionUtils.isNonEmpty(peerServices);
    }

    /**
     * @return requester consume services
     */
    public boolean isConsumeServicesOnly() {
        return requestHandler.everConsumed() && CollectionUtils.isEmpty(peerServices);
    }

    /** 获取requester ip */
    private String getRemoteAddress(RSocket requesterSocket) {
        try {
            Method remoteAddressMethod = ReflectionUtils.findMethod(DuplexConnection.class, "remoteAddress");
            if (remoteAddressMethod != null) {
                Field connectionField = ReflectionUtils.findField(requesterSocket.getClass(), "connection");
                if (connectionField != null) {
                    DuplexConnection connection = (DuplexConnection) ReflectionUtils.getField(connectionField, requesterSocket);
                    SocketAddress remoteAddress = (SocketAddress) remoteAddressMethod.invoke(connection);
                    if (remoteAddress instanceof InetSocketAddress) {
                        return ((InetSocketAddress) remoteAddress).getHostName();
                    }
                }
            }
        } catch (Exception ignore) {
            //do nothing
        }
        return "";
    }

    //getter
    public String getUuid() {
        return appMetadata.getUuid();
    }

    public Integer getId() {
        return appMetadata.getId();
    }

    public String getRemoteIp() {
        return remoteIp;
    }

    public AppMetadata getAppMetadata() {
        return appMetadata;
    }

    public RSocketAppPrincipal getPrincipal() {
        return requestHandler.getPrincipal();
    }

    public Set<Integer> getAppTagsHashCodeSet() {
        return appTagsHashCodeSet;
    }

    public AppStatus getAppStatus() {
        return appStatus;
    }

    public void setAppStatus(AppStatus appStatus) {
        this.appStatus = appStatus;
    }

    RSocketServiceRequestHandler getRequestHandler() {
        return requestHandler;
    }
}
