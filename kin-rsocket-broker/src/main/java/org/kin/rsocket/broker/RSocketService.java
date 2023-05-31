package org.kin.rsocket.broker;

import io.cloudevents.CloudEvent;
import io.rsocket.DuplexConnection;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import org.kin.framework.utils.CollectionUtils;
import org.kin.rsocket.auth.RSocketAppPrincipal;
import org.kin.rsocket.core.Endpoints;
import org.kin.rsocket.core.RSocketMimeType;
import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.domain.AppStatus;
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

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.*;

/**
 * service <- broker
 * broker向service发送请求
 * broker端缓存的rsocket service应用信息以及requester入口
 *
 * @author huangjianqin
 * @date 2021/3/30
 */
public final class RSocketService implements CloudEventRSocket {
    private static final Logger log = LoggerFactory.getLogger(RSocketService.class);
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
    private final RSocketServiceRegistry serviceRegistry;
    private final Mono<Void> comboOnClose;
    /** remote requester ip */
    private final String remoteIp;
    /** peer RSocket暴露的服务 */
    private final Set<ServiceLocator> peerServices;
    /** app status */
    private AppStatus appStatus = AppStatus.CONNECTED;
    /** rsocket consumer请求处理handler */
    private final RSocketServiceRequestHandler requestHandler;

    public RSocketService(RSocketCompositeMetadata compositeMetadata,
                          AppMetadata appMetadata,
                          RSocket requester,
                          RSocketServiceRegistry serviceRegistry,
                          RSocketServiceRequestHandler requestHandler) {
        this.appMetadata = appMetadata;
        //app tags hashcode set
        Set<Integer> appTagsHashCodeSet = new HashSet<>(4);
        appTagsHashCodeSet.add((Endpoints.INSTANCE_ID + appMetadata.getInstanceId()).hashCode());
        appTagsHashCodeSet.add((Endpoints.UUID + appMetadata.getUuid()).hashCode());

        if (appMetadata.getIp() != null && !appMetadata.getIp().isEmpty()) {
            appTagsHashCodeSet.add((Endpoints.IP + this.appMetadata.getIp()).hashCode());
        }

        if (CollectionUtils.isNonEmpty(appMetadata.getMetadata())) {
            for (Map.Entry<String, String> entry : appMetadata.getMetadata().entrySet()) {
                appTagsHashCodeSet.add((entry.getKey() + ":" + entry.getValue()).hashCode());
            }
        }
        this.appTagsHashCodeSet = Collections.unmodifiableSet(appTagsHashCodeSet);

        this.requester = requester;
        this.serviceRegistry = serviceRegistry;

        //publish services metadata
        this.peerServices = new HashSet<>();
        if (compositeMetadata.contains(RSocketMimeType.SERVICE_REGISTRY)) {
            RSocketServiceRegistryMetadata serviceRegistryMetadata = compositeMetadata.getMetadata(RSocketMimeType.SERVICE_REGISTRY);
            if (CollectionUtils.isNonEmpty(serviceRegistryMetadata.getPublished())) {
                peerServices.addAll(serviceRegistryMetadata.getPublished());
            }
        }

        //remote ip
        this.remoteIp = getRemoteAddress(requester);
        this.requestHandler = requestHandler;
        //new comboOnClose
        this.comboOnClose = Mono.firstWithSignal(requestHandler.onClose(), requester.onClose());
        this.comboOnClose.doOnTerminate(this::hideServices).subscribeOn(Schedulers.parallel()).subscribe();
    }

    @Nonnull
    @Override
    public Mono<Void> fireAndForget(@Nonnull Payload payload) {
        return requester.fireAndForget(payload);
    }

    @Nonnull
    @Override
    public Mono<Payload> requestResponse(@Nonnull Payload payload) {
        return requester.requestResponse(payload);
    }

    @Nonnull
    @Override
    public Flux<Payload> requestStream(@Nonnull Payload payload) {
        return requester.requestStream(payload);
    }

    @Nonnull
    @Override
    public Flux<Payload> requestChannel(@Nonnull Publisher<Payload> payloads) {
        return requester.requestChannel(payloads);
    }

    @Nonnull
    @Override
    public Mono<Void> metadataPush(@Nonnull Payload payload) {
        return requester.metadataPush(payload);
    }

    @Override
    public Mono<Void> fireCloudEvent(CloudEvent cloudEvent) {
        try {
            Payload payload = CloudEventSupport.cloudEvent2Payload(cloudEvent);
            return metadataPush(payload);
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    @Override
    public Mono<Void> fireCloudEvent(byte[] cloudEventBytes) {
        try {
            Payload payload = CloudEventSupport.cloudEventBytes2Payload(cloudEventBytes);
            return metadataPush(payload);
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    @Nonnull
    @Override
    public Mono<Void> onClose() {
        return this.comboOnClose;
    }

    /**
     * 暴露peer rsocket的服务, 并修改该app的服务状态
     */
    public void publishServices() {
        if (CollectionUtils.isNonEmpty(this.peerServices)) {
            Set<Integer> serviceIds = serviceRegistry.getServiceIds(appMetadata.getInstanceId());
            if (serviceIds.isEmpty()) {
                this.serviceRegistry.register(appMetadata.getInstanceId(), appMetadata.getWeight(), peerServices);
                this.appStatus = AppStatus.SERVING;
            }
        }
    }

    /** 注册指定服务 */
    public void registerServices(Collection<ServiceLocator> services) {
        this.peerServices.addAll(services);
        this.serviceRegistry.register(appMetadata.getInstanceId(), appMetadata.getWeight(), services);
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
        serviceRegistry.unregister(appMetadata.getInstanceId(), appMetadata.getWeight());
        this.appStatus = AppStatus.DOWN;
    }

    /** 注销指定服务 */
    public void unregisterServices(Collection<ServiceLocator> services) {
        if (this.peerServices != null && !this.peerServices.isEmpty()) {
            this.peerServices.removeAll(services);
        }
        for (ServiceLocator service : services) {
            this.serviceRegistry.unregister(appMetadata.getInstanceId(), appMetadata.getWeight(), service.getId());
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
        return !requestHandler.everConsumed() && CollectionUtils.isNonEmpty(peerServices);
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

    /**
     * 强制dispose
     * 目前仅有rsocket service主动请求要求强制dispose
     */
    public void forceDispose() {
        setAppStatus(AppStatus.STOPPED);
        requestHandler.dispose();
        requester.dispose();
    }

    //getter
    public String getUuid() {
        return appMetadata.getUuid();
    }

    public Integer getId() {
        return appMetadata.getInstanceId();
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

    RSocket getRequester() {
        return requester;
    }
}
