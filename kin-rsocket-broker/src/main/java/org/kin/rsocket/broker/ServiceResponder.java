package org.kin.rsocket.broker;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import io.rsocket.ConnectionSetupPayload;
import io.rsocket.DuplexConnection;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.exceptions.ApplicationErrorException;
import io.rsocket.exceptions.InvalidException;
import io.rsocket.frame.FrameType;
import io.rsocket.metadata.WellKnownMimeType;
import io.rsocket.util.ByteBufPayload;
import org.kin.framework.utils.CollectionUtils;
import org.kin.rsocket.auth.RSocketAppPrincipal;
import org.kin.rsocket.core.ReactiveServiceRegistry;
import org.kin.rsocket.core.ResponderRsocket;
import org.kin.rsocket.core.ResponderSupport;
import org.kin.rsocket.core.ServiceLocator;
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
import reactor.extra.processor.TopicProcessor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * service <- broker responder
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
    private final RSocket upstreamRSocket;
    /** reactive service routing selector */
    private final ServiceRouteTable routeTable;
    private final ServiceRouter serviceRouter;
    private final ServiceMeshInspector serviceMeshInspector;
    private final Mono<Void> comboOnClose;
    /** reactive event processor */
    private final TopicProcessor<CloudEventData<?>> eventProcessor;
    /** UUID from requester side */
    private final String uuid;
    /** remote requester ip */
    private final String remoteIp;
    /** app instance id */
    private final Integer id;

    /** default message mime type metadata */
    private MessageMimeTypeMetadata defaultMessageMimeType;
    /** default data encoding bytebuf */
    private ByteBuf defaultEncodingBytebuf;
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
                            ServiceRouteTable routingSelector,
                            TopicProcessor<CloudEventData<?>> eventProcessor,
                            ServiceRouter handlerRegistry,
                            ServiceMeshInspector serviceMeshInspector,
                            RSocket upstreamRSocket,
                            RSocketFilterChain filterChain,
                            ReactiveServiceRegistry serviceRegistry
    ) {
        super(serviceRegistry);
        this.upstreamRSocket = upstreamRSocket;
        RSocketMimeType dataType = RSocketMimeType.getByType(setupPayload.dataMimeType());
        if (dataType != null) {
            this.defaultMessageMimeType = MessageMimeTypeMetadata.of(dataType);
            this.defaultEncodingBytebuf = constructDefaultDataEncoding();
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
        this.routeTable = routingSelector;
        this.eventProcessor = eventProcessor;
        this.serviceRouter = handlerRegistry;
        this.serviceMeshInspector = serviceMeshInspector;
        this.filterChain = filterChain;
        //publish services metadata
        if (compositeMetadata.contains(RSocketMimeType.ServiceRegistry)) {
            ServiceRegistryMetadata serviceRegistryMetadata = ServiceRegistryMetadata.of(compositeMetadata.getMetadata(RSocketMimeType.ServiceRegistry));
            if (CollectionUtils.isNonEmpty(serviceRegistryMetadata.getPublished())) {
                setPeerServices(serviceRegistryMetadata.getPublished());
                registerPublishedServices();
            }
        }
        //remote ip
        this.remoteIp = getRemoteAddress(peerRsocket);
        //new comboOnClose
        this.comboOnClose = Mono.first(super.onClose(), peerRsocket.onClose());
        this.comboOnClose.doOnTerminate(() -> {
            unregisterPublishedServices();
            ReferenceCountUtil.release(this.defaultEncodingBytebuf);
        }).subscribeOn(Schedulers.parallel()).subscribe();
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
        Integer serviceId = gsvRoutingMetadata.id();
        boolean encodingMetadataIncluded = compositeMetadata.contains(RSocketMimeType.MessageMimeType);

        // broker local service call check: don't introduce interceptor, performance consideration
        if (serviceRegistry.contains(serviceId)) {
            return localRequestResponse(gsvRoutingMetadata, defaultMessageMimeType, null, payload);
        }
        //request filters
        Mono<RSocket> destination = findDestination(gsvRoutingMetadata);
        if (this.filterChain.isFiltersPresent()) {
            RSocketFilterContext filterContext = RSocketFilterContext.of(FrameType.REQUEST_RESPONSE, gsvRoutingMetadata, this.appMetadata, payload);
            destination = filterChain.filter(filterContext).then(destination);
        }
        //call destination
        return destination.flatMap(rsocket -> {
            recordServiceInvoke(gsvRoutingMetadata.gsv());
            if (encodingMetadataIncluded) {
                return rsocket.requestResponse(payload);
            } else {
                return rsocket.requestResponse(payloadWithDataEncoding(payload));
            }
        });
    }

    @Override
    public Mono<Void> fireAndForget(Payload payload) {
        RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(payload.metadata());
        GSVRoutingMetadata gsvRoutingMetadata = compositeMetadata.getMetadata(RSocketMimeType.Routing);
        if (gsvRoutingMetadata == null) {
            return Mono.error(new InvalidException("No Routing metadata"));
        }
        Integer serviceId = gsvRoutingMetadata.id();
        boolean encodingMetadataIncluded = compositeMetadata.contains(RSocketMimeType.MessageMimeType);
        if (serviceRegistry.contains(serviceId)) {
            return localFireAndForget(gsvRoutingMetadata, defaultMessageMimeType, payload);
        }
        //request filters
        Mono<RSocket> destination = findDestination(gsvRoutingMetadata);
        if (this.filterChain.isFiltersPresent()) {
            RSocketFilterContext filterContext = RSocketFilterContext.of(FrameType.REQUEST_FNF, gsvRoutingMetadata, this.appMetadata, payload);
            destination = filterChain.filter(filterContext).then(destination);
        }
        //call destination
        return destination.flatMap(rsocket -> {
            recordServiceInvoke(gsvRoutingMetadata.gsv());
            if (encodingMetadataIncluded) {
                return rsocket.fireAndForget(payload);
            } else {
                return rsocket.fireAndForget(payloadWithDataEncoding(payload));
            }
        });
    }

    @Override
    public Flux<Payload> requestStream(Payload payload) {
        RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(payload.metadata());
        GSVRoutingMetadata gsvRoutingMetadata = compositeMetadata.getMetadata(RSocketMimeType.Routing);
        if (gsvRoutingMetadata == null) {
            return Flux.error(new InvalidException("No Routing metadata"));
        }
        Integer serviceId = gsvRoutingMetadata.id();
        boolean encodingMetadataIncluded = compositeMetadata.contains(RSocketMimeType.MessageMimeType);
        if (serviceRegistry.contains(serviceId)) {
            return localRequestStream(gsvRoutingMetadata, defaultMessageMimeType, null, payload);
        }
        Mono<RSocket> destination = findDestination(gsvRoutingMetadata);
        if (this.filterChain.isFiltersPresent()) {
            RSocketFilterContext filterContext = RSocketFilterContext.of(FrameType.REQUEST_STREAM, gsvRoutingMetadata, this.appMetadata, payload);
            destination = filterChain.filter(filterContext).then(destination);
        }
        return destination.flatMapMany(rsocket -> {
            recordServiceInvoke(gsvRoutingMetadata.gsv());
            if (encodingMetadataIncluded) {
                return rsocket.requestStream(payload);
            } else {
                return rsocket.requestStream(payloadWithDataEncoding(payload));
            }
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
            return Mono.fromRunnable(() -> eventProcessor.onNext(cloudEvent));
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
        if (this.peerServices != null && !this.peerServices.isEmpty()) {
            Set<Integer> serviceIds = routeTable.getServiceIds(appMetadata.getId());
            if (serviceIds.isEmpty()) {
                this.routeTable.register(appMetadata.getId(), appMetadata.getPowerRating(), peerServices);
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
        this.routeTable.register(appMetadata.getId(), appMetadata.getPowerRating(), services);
    }

    /** 注销downstream暴露的服务 */
    public void unregisterPublishedServices() {
        routeTable.unregister(appMetadata.getId());
        this.appStatus = AppStatus.DOWN;
    }

    /** 注销指定服务 */
    public void unregisterServices(Set<ServiceLocator> services) {
        if (this.peerServices != null && !this.peerServices.isEmpty()) {
            this.peerServices.removeAll(services);
        }
        for (ServiceLocator service : services) {
            this.routeTable.unregister(appMetadata.getId(), service.getId());
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
    private Payload payloadWithDataEncoding(Payload payload) {
        CompositeByteBuf compositeByteBuf = new CompositeByteBuf(PooledByteBufAllocator.DEFAULT, true, 2, payload.metadata(), this.defaultEncodingBytebuf.retainedDuplicate());
        return ByteBufPayload.create(payload.data(), compositeByteBuf);
    }

    /** 构建默认的数据编码 */
    private ByteBuf constructDefaultDataEncoding() {
        ByteBuf buf = Unpooled.buffer(5, 5);
        buf.writeByte((byte) (WellKnownMimeType.MESSAGE_RSOCKET_MIMETYPE.getIdentifier() | 0x80));
        buf.writeByte(0);
        buf.writeByte(0);
        buf.writeByte(1);
        buf.writeByte(defaultMessageMimeType.getMessageMimeType().getId() | 0x80);
        return buf;
    }

    /**
     * 寻找目标服务provider instance
     */
    private Mono<RSocket> findDestination(GSVRoutingMetadata routingMetaData) {
        return Mono.create(sink -> {
            String gsv = routingMetaData.gsv();
            Integer serviceId = routingMetaData.id();
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
                    Integer targetInstanceId = routeTable.getInstanceId(serviceId);
                    if (targetInstanceId != null) {
                        targetResponder = serviceRouter.getByInstanceId(targetInstanceId);
                    } else {
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
                if (upstreamRSocket != null && error instanceof InvalidException) {
                    sink.success(upstreamRSocket);
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
            return serviceRouter.getByUUID(endpoint.substring(3));
        }
        int endpointHashCode = endpoint.hashCode();
        for (Integer instanceId : routeTable.getAllInstanceIds(serviceId)) {
            ServiceResponder responder = serviceRouter.getByInstanceId(instanceId);
            if (responder != null) {
                if (responder.appTagsHashCodeSet.contains(endpointHashCode)) {
                    return responder;
                }
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
            return serviceRouter.getByInstanceId(stickyServices.get(serviceId));
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
