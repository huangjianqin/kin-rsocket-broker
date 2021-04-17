package org.kin.rsocket.broker;

import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.util.ReferenceCountUtil;
import io.rsocket.ConnectionSetupPayload;
import io.rsocket.DuplexConnection;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.exceptions.ApplicationErrorException;
import io.rsocket.exceptions.InvalidException;
import io.rsocket.frame.FrameType;
import io.rsocket.util.ByteBufPayload;
import org.kin.framework.utils.CollectionUtils;
import org.kin.rsocket.auth.RSocketAppPrincipal;
import org.kin.rsocket.core.*;
import org.kin.rsocket.core.domain.AppStatus;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.CloudEventRSocket;
import org.kin.rsocket.core.event.CloudEventReply;
import org.kin.rsocket.core.metadata.*;
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
import java.net.URI;
import java.util.*;

/**
 * service <- broker
 * broker responder
 *
 * @author huangjianqin
 * @date 2021/3/30
 */
public class ServiceResponder extends ResponderSupport implements CloudEventRSocket, ResponderRsocket {
    private static final Logger log = LoggerFactory.getLogger(ServiceResponder.class);
    /** rsocket filter for requests */
    private final RSocketFilterChain filterChain;
    /** app metadata */
    private final AppMetadata appMetadata;
    /** app tags hashcode hash set, to make routing based on endpoint fast */
    private final Set<Integer> appTagsHashCodeSet = new HashSet<>();
    /** authorized principal */
    private final RSocketAppPrincipal principal;
    /** sticky services, key -> serviceId, value -> instanceId */
    private final Map<Integer, Integer> stickyServices = new HashMap<>();
    /** 记录请求过的服务id */
    private final Set<String> consumedServices = new HashSet<>();
    /** peer RSocket: sending or requester RSocket */
    private final RSocket peerRsocket;
    /** upstream broker */
    private final UpstreamCluster upstreamBrokers;
    private final ServiceManager serviceManager;
    private final ServiceMeshInspector serviceMeshInspector;
    private final Mono<Void> comboOnClose;
    /** UUID from requester side */
    private final String uuid;
    /** remote requester ip */
    private final String remoteIp;
    /** app instance id */
    private final Integer id;

    /** default message mime type metadata */
    private MessageMimeTypeMetadata defaultMessageMimeTypeMetadata;
    /** peer services */
    private Set<ServiceLocator> peerServices;
    /** app status */
    private AppStatus appStatus = AppStatus.CONNECTED;

    //TODO 构造器优化
    public ServiceResponder(ConnectionSetupPayload setupPayload,
                            RSocketCompositeMetadata compositeMetadata,
                            AppMetadata appMetadata,
                            RSocketAppPrincipal principal,
                            RSocket peerRsocket,
                            ServiceManager handlerRegistry,
                            ServiceMeshInspector serviceMeshInspector,
                            UpstreamCluster upstreamBrokers,
                            RSocketFilterChain filterChain
    ) {
        this.upstreamBrokers = upstreamBrokers;
        RSocketMimeType dataType = RSocketMimeType.getByType(setupPayload.dataMimeType());
        if (dataType != null) {
            this.defaultMessageMimeTypeMetadata = MessageMimeTypeMetadata.of(dataType);
        } else {
            //todo 如果downstream没有setup默认的编码类型, 则默认json
            this.defaultMessageMimeTypeMetadata = MessageMimeTypeMetadata.of(RSocketMimeType.Json);
        }
        this.appMetadata = appMetadata;
        this.id = appMetadata.getId();
        this.uuid = appMetadata.getUuid();
        //app tags hashcode set
        this.appTagsHashCodeSet.add(("id:" + this.id).hashCode());
        this.appTagsHashCodeSet.add(("uuid:" + this.uuid).hashCode());
        if (appMetadata.getIp() != null && !appMetadata.getIp().isEmpty()) {
            this.appTagsHashCodeSet.add(("ip:" + this.appMetadata.getIp()).hashCode());
        }
        if (appMetadata.getMetadata() != null) {
            for (Map.Entry<String, String> entry : appMetadata.getMetadata().entrySet()) {
                this.appTagsHashCodeSet.add((entry.getKey() + ":" + entry.getValue()).hashCode());
            }
        }
        this.principal = principal;
        this.peerRsocket = peerRsocket;
        this.serviceManager = handlerRegistry;
        this.serviceMeshInspector = serviceMeshInspector;
        this.filterChain = filterChain;
        //publish services metadata
        if (compositeMetadata.contains(RSocketMimeType.ServiceRegistry)) {
            ServiceRegistryMetadata serviceRegistryMetadata = compositeMetadata.getMetadata(RSocketMimeType.ServiceRegistry);
            if (CollectionUtils.isNonEmpty(serviceRegistryMetadata.getPublished())) {
                setPeerServices(serviceRegistryMetadata.getPublished());
                registerPublishedServices();
            }
        }
        //remote ip
        this.remoteIp = getRemoteAddress(peerRsocket);
        //new comboOnClose
        this.comboOnClose = Mono.firstWithSignal(super.onClose(), peerRsocket.onClose());
        this.comboOnClose.doOnTerminate(this::unregisterPublishedServices).subscribeOn(Schedulers.parallel()).subscribe();
    }

    /** downstream暴露的服务 */
    private void setPeerServices(Set<ServiceLocator> services) {
        this.peerServices = services;
    }

    @Override
    public Mono<Payload> requestResponse(Payload payload) {
        //todo 比较多重复代码, 看看能不能优化
        RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(payload.metadata());
        GSVRoutingMetadata gsvRoutingMetadata = compositeMetadata.getMetadata(RSocketMimeType.Routing);
        if (gsvRoutingMetadata == null) {
            return Mono.error(new InvalidException("No Routing metadata"));
        }

        MessageMimeTypeMetadata messageMimeTypeMetadata = compositeMetadata.getMetadata(RSocketMimeType.MessageMimeType);
        if (Objects.isNull(messageMimeTypeMetadata)) {
            messageMimeTypeMetadata = defaultMessageMimeTypeMetadata;
        }

        // broker local service call check: don't introduce interceptor, performance consideration
        if (ReactiveServiceRegistry.INSTANCE.contains(gsvRoutingMetadata.handlerId())) {
            //todo accept mime type 通过配置实现
            return localRequestResponse(gsvRoutingMetadata, messageMimeTypeMetadata,
                    compositeMetadata.getMetadata(RSocketMimeType.MessageAcceptMimeTypes), payload);
        }
        //request filters
        Mono<RSocket> destination = findDestination(gsvRoutingMetadata);
        if (this.filterChain.isFiltersPresent()) {
            RSocketFilterContext filterContext = RSocketFilterContext.of(FrameType.REQUEST_RESPONSE, gsvRoutingMetadata, this.appMetadata, payload);
            destination = filterChain.filter(filterContext).then(destination);
        }
        //call destination
        MessageMimeTypeMetadata finalMessageMimeTypeMetadata = messageMimeTypeMetadata;
        return destination.flatMap(rsocket -> {
            recordServiceInvoke(gsvRoutingMetadata.gsv());
            return rsocket.requestResponse(payloadWithDataEncoding(payload, finalMessageMimeTypeMetadata));
        });
    }

    @Override
    public Mono<Void> fireAndForget(Payload payload) {
        RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(payload.metadata());
        GSVRoutingMetadata gsvRoutingMetadata = compositeMetadata.getMetadata(RSocketMimeType.Routing);
        if (gsvRoutingMetadata == null) {
            return Mono.error(new InvalidException("No Routing metadata"));
        }

        MessageMimeTypeMetadata messageMimeTypeMetadata = compositeMetadata.getMetadata(RSocketMimeType.MessageMimeType);
        if (Objects.isNull(messageMimeTypeMetadata)) {
            messageMimeTypeMetadata = defaultMessageMimeTypeMetadata;
        }

        if (ReactiveServiceRegistry.INSTANCE.contains(gsvRoutingMetadata.handlerId())) {
            return localFireAndForget(gsvRoutingMetadata, messageMimeTypeMetadata, payload);
        }
        //request filters
        Mono<RSocket> destination = findDestination(gsvRoutingMetadata);
        if (this.filterChain.isFiltersPresent()) {
            RSocketFilterContext filterContext = RSocketFilterContext.of(FrameType.REQUEST_FNF, gsvRoutingMetadata, this.appMetadata, payload);
            destination = filterChain.filter(filterContext).then(destination);
        }
        //call destination
        MessageMimeTypeMetadata finalMessageMimeTypeMetadata = messageMimeTypeMetadata;
        return destination.flatMap(rsocket -> {
            recordServiceInvoke(gsvRoutingMetadata.gsv());
            return rsocket.fireAndForget(payloadWithDataEncoding(payload, finalMessageMimeTypeMetadata));
        });
    }

    @Override
    public Flux<Payload> requestStream(Payload payload) {
        RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(payload.metadata());
        GSVRoutingMetadata gsvRoutingMetadata = compositeMetadata.getMetadata(RSocketMimeType.Routing);
        if (gsvRoutingMetadata == null) {
            return Flux.error(new InvalidException("No Routing metadata"));
        }

        MessageMimeTypeMetadata messageMimeTypeMetadata = compositeMetadata.getMetadata(RSocketMimeType.MessageMimeType);
        if (Objects.isNull(messageMimeTypeMetadata)) {
            messageMimeTypeMetadata = defaultMessageMimeTypeMetadata;
        }
        if (ReactiveServiceRegistry.INSTANCE.contains(gsvRoutingMetadata.handlerId())) {
            return localRequestStream(gsvRoutingMetadata, messageMimeTypeMetadata,
                    compositeMetadata.getMetadata(RSocketMimeType.MessageAcceptMimeTypes), payload);
        }

        Mono<RSocket> destination = findDestination(gsvRoutingMetadata);
        if (this.filterChain.isFiltersPresent()) {
            RSocketFilterContext filterContext = RSocketFilterContext.of(FrameType.REQUEST_STREAM, gsvRoutingMetadata, this.appMetadata, payload);
            destination = filterChain.filter(filterContext).then(destination);
        }
        MessageMimeTypeMetadata finalMessageMimeTypeMetadata = messageMimeTypeMetadata;
        return destination.flatMapMany(rsocket -> {
            recordServiceInvoke(gsvRoutingMetadata.gsv());
            return rsocket.requestStream(payloadWithDataEncoding(payload, finalMessageMimeTypeMetadata));
        });
    }

    private Flux<Payload> requestChannel(Payload signal, Flux<Payload> payloads) {
        RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(signal.metadata());
        GSVRoutingMetadata gsvRoutingMetadata = compositeMetadata.getMetadata(RSocketMimeType.Routing);
        if (gsvRoutingMetadata == null) {
            return Flux.error(new InvalidException("No Routing metadata"));
        }

        Mono<RSocket> destination = findDestination(gsvRoutingMetadata);
        return destination.flatMapMany(rsocket -> {
            recordServiceInvoke(gsvRoutingMetadata.gsv());
            return rsocket.requestChannel(payloads);
        });
    }

    @Override
    public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
        Flux<Payload> payloadsWithSignalRouting = (Flux<Payload>) payloads;
        return payloadsWithSignalRouting.switchOnFirst((signal, flux) -> requestChannel(signal.get(), flux));
    }

    @Override
    public Mono<Void> metadataPush(Payload payload) {
        try {
            if (payload.metadata().readableBytes() > 0) {
                CloudEventData<?> cloudEvent = extractCloudEventsFromMetadata(payload);
                if (cloudEvent != null) {
                    return fireCloudEvent(cloudEvent);
                }
            }
        } catch (Exception e) {
            log.error(String.format("Failed to parse Cloud Event: %s", e.getMessage()), e);
        } finally {
            ReferenceCountUtil.safeRelease(payload);
        }
        return Mono.empty();
    }

    @Override
    public Mono<Void> fireCloudEvent(CloudEventData<?> cloudEvent) {
        //todo 要进行event的安全验证，不合法来源的event进行消费，后续还好进行event判断
        if (uuid.equalsIgnoreCase(cloudEvent.getAttributes().getSource().getHost())) {
            return Mono.fromRunnable(() -> RSocketAppContext.CLOUD_EVENT_SINK.tryEmitNext(cloudEvent));
        }
        return Mono.empty();
    }

    @Override
    public Mono<Void> fireCloudEventReply(URI replayTo, CloudEventReply eventReply) {
        return peerRsocket.fireAndForget(cloudEventReply2Payload(replayTo, eventReply));
    }

    @Override
    public Mono<Void> fireCloudEventToPeer(CloudEventData<?> cloudEvent) {
        try {
            Payload payload = cloudEvent2Payload(cloudEvent);
            return peerRsocket.metadataPush(payload);
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    /** 注册downstream暴露的服务 */
    public void registerPublishedServices() {
        //todo 此处, 是connection setup时注册, registerServices是通过cloudevent注册, 是否考虑仅存一个即可
        if (this.peerServices != null && !this.peerServices.isEmpty()) {
            Set<Integer> serviceIds = serviceManager.getServiceIds(appMetadata.getId());
            if (serviceIds.isEmpty()) {
                this.serviceManager.register(appMetadata.getId(), appMetadata.getPowerRating(), peerServices);
                this.appStatus = AppStatus.SERVING;
            }
        }
    }

    /** 注册指定服务 */
    public void registerServices(Set<ServiceLocator> services) {
        if (this.peerServices == null || this.peerServices.isEmpty()) {
            this.peerServices = services;
        } else {
            this.peerServices.addAll(services);
        }
        this.serviceManager.register(appMetadata.getId(), appMetadata.getPowerRating(), services);
    }

    /** 注销downstream暴露的服务 */
    public void unregisterPublishedServices() {
        serviceManager.unregister(appMetadata.getId());
        this.appStatus = AppStatus.DOWN;
    }

    /** 注销指定服务 */
    public void unregisterServices(Set<ServiceLocator> services) {
        if (this.peerServices != null && !this.peerServices.isEmpty()) {
            this.peerServices.removeAll(services);
        }
        for (ServiceLocator service : services) {
            this.serviceManager.unregister(appMetadata.getId(), service.getId());
        }
    }

    /**
     * 记录请求过的服务id
     */
    private void recordServiceInvoke(String serviceId) {
        consumedServices.add(serviceId);
    }

    /**
     * @return downstream publish services only
     */
    public boolean isPublishServicesOnly() {
        return CollectionUtils.isEmpty(consumedServices) && CollectionUtils.isNonEmpty(peerServices);
    }

    /**
     * @return downstream consume and publish services
     */
    public boolean isConsumeAndPublishServices() {
        return CollectionUtils.isNonEmpty(consumedServices) && CollectionUtils.isNonEmpty(peerServices);
    }

    /**
     * @return downstream consume services
     */
    public boolean isConsumeServicesOnly() {
        return CollectionUtils.isNonEmpty(consumedServices) && CollectionUtils.isEmpty(peerServices);
    }

    /**
     * payload with data encoding
     */
    private Payload payloadWithDataEncoding(Payload payload, MessageMimeTypeMetadata messageMimeTypeMetadata) {
        CompositeByteBuf compositeByteBuf = new CompositeByteBuf(PooledByteBufAllocator.DEFAULT, true, 2,
                payload.metadata(), MessageMimeTypeMetadata.toByteBuf(messageMimeTypeMetadata));
        return ByteBufPayload.create(payload.data(), compositeByteBuf);
    }

    /**
     * 寻找目标服务provider instance
     */
    private Mono<RSocket> findDestination(GSVRoutingMetadata routingMetaData) {
        return Mono.create(sink -> {
            String gsv = routingMetaData.gsv();
            Integer serviceId = routingMetaData.serviceId();
            RSocket rsocket = null;
            Exception error = null;
            //sticky session responder
            boolean sticky = routingMetaData.isSticky();
            ServiceResponder targetResponder = findStickyHandler(sticky, serviceId);
            // responder from sticky services
            if (targetResponder != null) {
                rsocket = targetResponder.peerRsocket;
            } else {
                String endpoint = routingMetaData.getEndpoint();
                if (endpoint != null && !endpoint.isEmpty()) {
                    targetResponder = findDestinationWithEndpoint(endpoint, serviceId);
                    if (targetResponder == null) {
                        error = new InvalidException(String.format("Service not found with endpoint '%s' '%s'", gsv, endpoint));
                    }
                } else {
                    targetResponder = serviceManager.getByServiceId(serviceId);
                    if (Objects.isNull(targetResponder)) {
                        error = new InvalidException(String.format("Service not found '%s'", gsv));
                    }
                }
                if (targetResponder != null) {
                    if (serviceMeshInspector.isAllowed(this.principal, gsv, targetResponder.principal)) {
                        rsocket = targetResponder.peerRsocket;
                        //save responder id if sticky
                        if (sticky) {
                            this.stickyServices.put(serviceId, targetResponder.getId());
                        }
                    } else {
                        error = new ApplicationErrorException(String.format("Service request not allowed '%s'", gsv));
                    }
                }
            }
            if (rsocket != null) {
                sink.success(rsocket);
            } else if (error != null) {
                if (upstreamBrokers != null && error instanceof InvalidException) {
                    sink.success(upstreamBrokers);
                } else {
                    sink.error(error);
                }
            } else {
                sink.error(new ApplicationErrorException(String.format("Service not found '%s'", gsv)));
            }
        });
    }

    /**
     * todo
     */
    private ServiceResponder findDestinationWithEndpoint(String endpoint, Integer serviceId) {
        if (endpoint.startsWith("id:")) {
            return serviceManager.getByUUID(endpoint.substring(3));
        }
        int endpointHashCode = endpoint.hashCode();
        for (ServiceResponder responder : serviceManager.getAllByServiceId(serviceId)) {
            if (responder.appTagsHashCodeSet.contains(endpointHashCode)) {
                return responder;
            }
        }
        return null;
    }

    @Override
    public Mono<Void> onClose() {
        return this.comboOnClose;
    }

    /**
     * todo
     */
    private ServiceResponder findStickyHandler(boolean sticky, Integer serviceId) {
        // todo 算法更新，如一致性hash算法，或者取余操作
        if (sticky && stickyServices.containsKey(serviceId)) {
            return serviceManager.getByInstanceId(stickyServices.get(serviceId));
        }
        return null;
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

        }
        return null;
    }

    //getter
    public String getUuid() {
        return this.uuid;
    }

    public Integer getId() {
        return id;
    }


    public String getRemoteIp() {
        return remoteIp;
    }

    public AppMetadata getAppMetadata() {
        return appMetadata;
    }

    public Set<String> getConsumedServices() {
        return consumedServices;
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

    public RSocketAppPrincipal getPrincipal() {
        return principal;
    }

    public RSocket getPeerRsocket() {
        return peerRsocket;
    }
}