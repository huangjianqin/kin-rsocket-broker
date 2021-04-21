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
import org.kin.framework.collection.ConcurrentHashSet;
import org.kin.framework.utils.CollectionUtils;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.auth.RSocketAppPrincipal;
import org.kin.rsocket.core.*;
import org.kin.rsocket.core.domain.AppStatus;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.CloudEventRSocket;
import org.kin.rsocket.core.event.CloudEventReply;
import org.kin.rsocket.core.metadata.*;
import org.kin.rsocket.core.utils.UriUtils;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * service -> broker
 * broker responder, 处理service request
 *
 * @author huangjianqin
 * @date 2021/3/30
 */
public final class BrokerResponder extends ResponderSupport implements CloudEventRSocket, ResponderRsocket {
    private static final Logger log = LoggerFactory.getLogger(BrokerResponder.class);
    /** rsocket filter for requests */
    private final RSocketFilterChain filterChain;
    /** app metadata */
    private final AppMetadata appMetadata;
    /**
     * peer service app的id uuid ip以及metedata字段hashcode
     * 目前用于根据endpoint 快速路由
     * 不可变
     */
    private final Set<Integer> appTagsHashCodeSet;
    /** authorized principal */
    private final RSocketAppPrincipal principal;
    /** sticky services, key -> serviceId, value -> instanceId */
    private final Map<Integer, Integer> stickyServices = new ConcurrentHashMap<>();
    /** 记录请求过的服务id */
    private final Set<String> consumedServices = new ConcurrentHashSet<>();
    /** peer RSocket, requester RSocket */
    private final RSocket peerRsocket;
    /** upstream broker */
    private final UpstreamCluster upstreamBrokers;
    private final ServiceManager serviceManager;
    private final ServiceMeshInspector serviceMeshInspector;
    private final Mono<Void> comboOnClose;
    /** remote requester ip */
    private final String remoteIp;
    /** default message mime type metadata */
    private final MessageMimeTypeMetadata defaultMessageMimeTypeMetadata;
    /** peer RSocket暴露的服务 */
    private Set<ServiceLocator> peerServices;
    /** app status */
    private AppStatus appStatus = AppStatus.CONNECTED;

    public BrokerResponder(ConnectionSetupPayload setupPayload,
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
            //如果requester的RSocketConnector没有设置dataMimeType(), 则默认json
            this.defaultMessageMimeTypeMetadata = MessageMimeTypeMetadata.of(RSocketMimeType.defaultEncodingType());
        }

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

        this.principal = principal;
        this.peerRsocket = peerRsocket;
        this.serviceManager = handlerRegistry;
        this.serviceMeshInspector = serviceMeshInspector;
        this.filterChain = filterChain;

        //publish services metadata
        this.peerServices = new HashSet<>();
        if (compositeMetadata.contains(RSocketMimeType.ServiceRegistry)) {
            ServiceRegistryMetadata serviceRegistryMetadata = compositeMetadata.getMetadata(RSocketMimeType.ServiceRegistry);
            if (CollectionUtils.isNonEmpty(serviceRegistryMetadata.getPublished())) {
                peerServices.addAll(serviceRegistryMetadata.getPublished());
                publishServices();
            }
        }


        //remote ip
        this.remoteIp = getRemoteAddress(peerRsocket);
        //new comboOnClose
        this.comboOnClose = Mono.firstWithSignal(super.onClose(), peerRsocket.onClose());
        this.comboOnClose.doOnTerminate(this::hideServices).subscribeOn(Schedulers.parallel()).subscribe();
    }

    @Override
    public Mono<Payload> requestResponse(Payload payload) {
        RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(payload.metadata());
        GSVRoutingMetadata gsvRoutingMetadata = compositeMetadata.getMetadata(RSocketMimeType.Routing);
        if (gsvRoutingMetadata == null) {
            return Mono.error(new InvalidException("No Routing metadata"));
        }

        MessageMimeTypeMetadata messageMimeTypeMetadata = compositeMetadata.getMetadata(RSocketMimeType.MessageMimeType);
        if (Objects.isNull(messageMimeTypeMetadata)) {
            messageMimeTypeMetadata = defaultMessageMimeTypeMetadata;
        }

        // broker local service call
        if (ReactiveServiceRegistry.INSTANCE.contains(gsvRoutingMetadata.handlerId())) {
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

        // broker local service call
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

        // broker local service call
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
        /**
         * 如果不是该responder对应的app uuid的cloud event, 则不处理
         * 因为broker需要做拦截处理, 防止该app修改别的app
         */
        if (appMetadata.getUuid().equalsIgnoreCase(UriUtils.getAppUUID(cloudEvent.getAttributes().getSource()))) {
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

    /**
     * 此处本质上可以在原来的{@link RSocketCompositeMetadata}上添加{@link MessageMimeTypeMetadata},
     * 然后再调用{@link RSocketCompositeMetadata#getContent()}来获取路由需要的payload, 之所以采用下面这种方式,
     * 可以减少一点点ByteBuf的内存资源分配和cpu消耗
     */
    private Payload payloadWithDataEncoding(Payload payload, MessageMimeTypeMetadata messageMimeTypeMetadata) {
        CompositeByteBuf compositeByteBuf = new CompositeByteBuf(PooledByteBufAllocator.DEFAULT, true, 2,
                payload.metadata(), toMimeAndContentBuffersSlices(messageMimeTypeMetadata));
        return ByteBufPayload.create(payload.data(), compositeByteBuf);
    }

    /**
     * 构建{@link io.rsocket.metadata.CompositeMetadata}entry的bytes
     * 详细编解码过程可以看{@link io.rsocket.metadata.CompositeMetadataCodec#decodeMimeAndContentBuffersSlices}
     */
    private static ByteBuf toMimeAndContentBuffersSlices(MessageMimeTypeMetadata metadata) {
        ByteBuf buf = Unpooled.buffer(5, 5);
        buf.writeByte((byte) (WellKnownMimeType.MESSAGE_RSOCKET_MIMETYPE.getIdentifier() | 0x80));
        buf.writeByte(0);
        buf.writeByte(0);
        buf.writeByte(1);
        buf.writeByte(metadata.getMessageMimeType().getId() | 0x80);
        return buf;
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
            BrokerResponder targetResponder = null;
            if (sticky) {
                // responder from sticky services
                targetResponder = findStickyServiceInstance(serviceId);
            }

            if (targetResponder != null) {
                rsocket = targetResponder.peerRsocket;
            } else {
                String endpoint = routingMetaData.getEndpoint();
                if (StringUtils.isNotBlank(endpoint)) {
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
                //本地找不到, 请求其他broker帮忙处理
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
     * 根据endpoint属性寻找target service instance
     */
    private BrokerResponder findDestinationWithEndpoint(String endpoint, Integer serviceId) {
        if (endpoint.startsWith("id:")) {
            return serviceManager.getByUUID(endpoint.substring(3));
        }
        int endpointHashCode = endpoint.hashCode();
        for (BrokerResponder responder : serviceManager.getAllByServiceId(serviceId)) {
            if (responder.appTagsHashCodeSet.contains(endpointHashCode)) {
                return responder;
            }
        }
        return null;
    }

    /**
     * 寻找sticky service instance
     */
    private BrokerResponder findStickyServiceInstance(Integer serviceId) {
        if (stickyServices.containsKey(serviceId)) {
            return serviceManager.getByInstanceId(stickyServices.get(serviceId));
        }
        return null;
    }

    /**
     * 记录请求过的service id
     */
    private void recordServiceInvoke(String serviceId) {
        consumedServices.add(serviceId);
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
                this.serviceManager.register(appMetadata.getId(), appMetadata.getPowerRating(), peerServices);
                this.appStatus = AppStatus.SERVING;
            }
        }
    }

    /** 注册指定服务 */
    public void registerServices(Collection<ServiceLocator> services) {
        this.peerServices.addAll(services);
        this.serviceManager.register(appMetadata.getId(), appMetadata.getPowerRating(), services);
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
        return CollectionUtils.isEmpty(consumedServices) && CollectionUtils.isNonEmpty(peerServices);
    }

    /**
     * @return requester consume and publish services
     */
    public boolean isConsumeAndPublishServices() {
        return CollectionUtils.isNonEmpty(consumedServices) && CollectionUtils.isNonEmpty(peerServices);
    }

    /**
     * @return requester consume services
     */
    public boolean isConsumeServicesOnly() {
        return CollectionUtils.isNonEmpty(consumedServices) && CollectionUtils.isEmpty(peerServices);
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
