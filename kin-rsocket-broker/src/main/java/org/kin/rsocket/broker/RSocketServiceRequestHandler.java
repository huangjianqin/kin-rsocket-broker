package org.kin.rsocket.broker;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import io.rsocket.ConnectionSetupPayload;
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
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.CloudEventSupport;
import org.kin.rsocket.core.metadata.*;
import org.kin.rsocket.core.utils.UriUtils;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * service -> broker
 * broker处理service request
 *
 * @author huangjianqin
 * @date 2021/4/21
 */
public final class RSocketServiceRequestHandler extends RequestHandlerSupport {
    private static final Logger log = LoggerFactory.getLogger(RSocketServiceRequestHandler.class);
    /** rsocket filter for requests */
    private final RSocketFilterChain filterChain;
    /** app metadata */
    private final AppMetadata appMetadata;
    /** authorized principal */
    private final RSocketAppPrincipal principal;
    /** sticky services, key -> serviceId, value -> instanceId */
    private final Map<Integer, Integer> stickyServices = new ConcurrentHashMap<>();
    /** upstream broker */
    private final UpstreamCluster upstreamBrokers;
    private final RSocketServiceManager serviceManager;
    private final RSocketServiceMeshInspector serviceMeshInspector;
    /** default消息编码类型 */
    private final MessageMimeTypeMetadata defaultMessageMimeTypeMetadata;
    /** 记录请求过的服务id */
    private final Set<String> consumedServices = new ConcurrentHashSet<>();

    public RSocketServiceRequestHandler(ConnectionSetupPayload setupPayload,
                                        AppMetadata appMetadata,
                                        RSocketAppPrincipal principal,
                                        RSocketServiceManager serviceManager,
                                        RSocketServiceMeshInspector serviceMeshInspector,
                                        UpstreamCluster upstreamBrokers,
                                        RSocketFilterChain filterChain) {
        this.upstreamBrokers = upstreamBrokers;
        RSocketMimeType dataType = RSocketMimeType.getByType(setupPayload.dataMimeType());

        if (dataType != null) {
            this.defaultMessageMimeTypeMetadata = MessageMimeTypeMetadata.of(dataType);
        } else {
            //如果requester的RSocketConnector没有设置dataMimeType(), 则默认json
            this.defaultMessageMimeTypeMetadata = MessageMimeTypeMetadata.of(RSocketMimeType.defaultEncodingType());
        }

        this.appMetadata = appMetadata;
        this.principal = principal;
        this.serviceManager = serviceManager;
        this.serviceMeshInspector = serviceMeshInspector;
        this.filterChain = filterChain;
    }

    @Override
    public Mono<Payload> requestResponse(Payload payload) {
        BinaryRoutingMetadata binaryRoutingMetadata = BinaryRoutingMetadata.extract(payload.metadata());

        GSVRoutingMetadata gsvRoutingMetadata;
        boolean encodingMetadataIncluded;
        MessageMimeTypeMetadata messageMimeTypeMetadata;
        MessageAcceptMimeTypesMetadata acceptMimeTypesMetadata = null;
        if (Objects.isNull(binaryRoutingMetadata)) {
            //回退到取GSVRoutingMetadata
            RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(payload.metadata());
            gsvRoutingMetadata = compositeMetadata.getMetadata(RSocketMimeType.ROUTING);
            if (Objects.isNull(gsvRoutingMetadata)) {
                return Mono.error(new InvalidException("no routing metadata"));
            }
            messageMimeTypeMetadata = compositeMetadata.getMetadata(RSocketMimeType.MESSAGE_MIME_TYPE);
            acceptMimeTypesMetadata = compositeMetadata.getMetadata(RSocketMimeType.MESSAGE_ACCEPT_MIME_TYPES);
            encodingMetadataIncluded = Objects.nonNull(messageMimeTypeMetadata);

        } else {
            gsvRoutingMetadata = binaryRoutingMetadata.toGSVRoutingMetadata();
            //默认认为带了消息编码元数据
            encodingMetadataIncluded = true;
            messageMimeTypeMetadata = defaultMessageMimeTypeMetadata;
        }

        // broker local service call
        if (RSocketServiceRegistry.INSTANCE.contains(gsvRoutingMetadata.handlerId())) {
            RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(payload.metadata());
            messageMimeTypeMetadata = getDataEncodingMetadata(compositeMetadata);
            if (Objects.isNull(acceptMimeTypesMetadata)) {
                acceptMimeTypesMetadata = compositeMetadata.getMetadata(RSocketMimeType.MESSAGE_ACCEPT_MIME_TYPES);
            }

            return localRequestResponse(gsvRoutingMetadata, messageMimeTypeMetadata, acceptMimeTypesMetadata, payload);
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
            if (encodingMetadataIncluded) {
                return rsocket.requestResponse(payload);
            } else {
                return rsocket.requestResponse(payloadWithDataEncoding(payload, finalMessageMimeTypeMetadata));
            }
        });
    }

    @Override
    public Mono<Void> fireAndForget(Payload payload) {
        BinaryRoutingMetadata binaryRoutingMetadata = BinaryRoutingMetadata.extract(payload.metadata());

        GSVRoutingMetadata gsvRoutingMetadata;
        boolean encodingMetadataIncluded;
        MessageMimeTypeMetadata messageMimeTypeMetadata;
        if (Objects.isNull(binaryRoutingMetadata)) {
            //回退到取GSVRoutingMetadata
            RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(payload.metadata());
            gsvRoutingMetadata = compositeMetadata.getMetadata(RSocketMimeType.ROUTING);
            if (Objects.isNull(gsvRoutingMetadata)) {
                return Mono.error(new InvalidException("no routing metadata"));
            }
            messageMimeTypeMetadata = compositeMetadata.getMetadata(RSocketMimeType.MESSAGE_MIME_TYPE);
            encodingMetadataIncluded = Objects.nonNull(messageMimeTypeMetadata);

        } else {
            gsvRoutingMetadata = binaryRoutingMetadata.toGSVRoutingMetadata();
            //默认认为带了消息编码元数据
            encodingMetadataIncluded = true;
            messageMimeTypeMetadata = defaultMessageMimeTypeMetadata;
        }

        // broker local service call
        if (RSocketServiceRegistry.INSTANCE.contains(gsvRoutingMetadata.handlerId())) {
            RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(payload.metadata());
            messageMimeTypeMetadata = getDataEncodingMetadata(compositeMetadata);

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
            if (encodingMetadataIncluded) {
                return rsocket.fireAndForget(payload);
            } else {
                return rsocket.fireAndForget(payloadWithDataEncoding(payload, finalMessageMimeTypeMetadata));
            }
        });
    }

    @Override
    public Flux<Payload> requestStream(Payload payload) {
        BinaryRoutingMetadata binaryRoutingMetadata = BinaryRoutingMetadata.extract(payload.metadata());

        GSVRoutingMetadata gsvRoutingMetadata;
        boolean encodingMetadataIncluded;
        MessageMimeTypeMetadata messageMimeTypeMetadata;
        MessageAcceptMimeTypesMetadata acceptMimeTypesMetadata = null;
        if (Objects.isNull(binaryRoutingMetadata)) {
            //回退到取GSVRoutingMetadata
            RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(payload.metadata());
            gsvRoutingMetadata = compositeMetadata.getMetadata(RSocketMimeType.ROUTING);
            if (Objects.isNull(gsvRoutingMetadata)) {
                return Flux.error(new InvalidException("no routing metadata"));
            }
            messageMimeTypeMetadata = compositeMetadata.getMetadata(RSocketMimeType.MESSAGE_MIME_TYPE);
            acceptMimeTypesMetadata = compositeMetadata.getMetadata(RSocketMimeType.MESSAGE_ACCEPT_MIME_TYPES);
            encodingMetadataIncluded = Objects.nonNull(messageMimeTypeMetadata);

        } else {
            gsvRoutingMetadata = binaryRoutingMetadata.toGSVRoutingMetadata();
            //默认认为带了消息编码元数据
            encodingMetadataIncluded = true;
            messageMimeTypeMetadata = defaultMessageMimeTypeMetadata;
        }

        // broker local service call
        if (RSocketServiceRegistry.INSTANCE.contains(gsvRoutingMetadata.handlerId())) {
            RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(payload.metadata());
            messageMimeTypeMetadata = getDataEncodingMetadata(compositeMetadata);
            if (Objects.isNull(acceptMimeTypesMetadata)) {
                acceptMimeTypesMetadata = compositeMetadata.getMetadata(RSocketMimeType.MESSAGE_ACCEPT_MIME_TYPES);
            }

            return localRequestStream(gsvRoutingMetadata, messageMimeTypeMetadata, acceptMimeTypesMetadata, payload);
        }

        Mono<RSocket> destination = findDestination(gsvRoutingMetadata);
        if (this.filterChain.isFiltersPresent()) {
            RSocketFilterContext filterContext = RSocketFilterContext.of(FrameType.REQUEST_STREAM, gsvRoutingMetadata, this.appMetadata, payload);
            destination = filterChain.filter(filterContext).then(destination);
        }
        MessageMimeTypeMetadata finalMessageMimeTypeMetadata = messageMimeTypeMetadata;
        return destination.flatMapMany(rsocket -> {
            recordServiceInvoke(gsvRoutingMetadata.gsv());
            if (encodingMetadataIncluded) {
                return rsocket.requestStream(payload);
            } else {
                return rsocket.requestStream(payloadWithDataEncoding(payload, finalMessageMimeTypeMetadata));
            }
        });
    }

    private Flux<Payload> requestChannel(Payload signal, Flux<Payload> payloads) {
        BinaryRoutingMetadata binaryRoutingMetadata = BinaryRoutingMetadata.extract(signal.metadata());

        GSVRoutingMetadata gsvRoutingMetadata;
        if (Objects.isNull(binaryRoutingMetadata)) {
            //回退到取GSVRoutingMetadata
            RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(signal.metadata());
            gsvRoutingMetadata = compositeMetadata.getMetadata(RSocketMimeType.ROUTING);
            if (Objects.isNull(gsvRoutingMetadata)) {
                return Flux.error(new InvalidException("no routing metadata"));
            }
        } else {
            gsvRoutingMetadata = binaryRoutingMetadata.toGSVRoutingMetadata();
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
                CloudEventData<?> cloudEvent = CloudEventSupport.extractCloudEventsFromMetadata(payload);
                if (cloudEvent != null) {
                    /**
                     * 如果不是该responder对应的app uuid的cloud event, 则不处理
                     * 因为broker需要做拦截处理, 防止该app修改别的app
                     */
                    if (appMetadata.getUuid().equalsIgnoreCase(UriUtils.getAppUUID(cloudEvent.getAttributes().getSource()))) {
                        return Mono.fromRunnable(() -> RSocketAppContext.CLOUD_EVENT_SINK.tryEmitNext(cloudEvent));
                    }
                    return Mono.empty();
                }
            }
        } catch (Exception e) {
            log.error(String.format("Failed to parse Cloud Event: %s", e.getMessage()), e);
        } finally {
            ReferenceCountUtil.safeRelease(payload);
        }
        return Mono.empty();
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
                rsocket = targetResponder;
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
                    if (serviceMeshInspector.isAllowed(this.principal, gsv, targetResponder.getPrincipal())) {
                        rsocket = targetResponder;
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
            } else {
                //本地找不到, 请求其他broker帮忙处理
                if (upstreamBrokers != null && error instanceof InvalidException) {
                    sink.success(upstreamBrokers);
                } else {
                    sink.error(new ApplicationErrorException(String.format("Service not found '%s'", gsv), error));
                }
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
            if (responder.getAppTagsHashCodeSet().contains(endpointHashCode)) {
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

    /**
     * 解析并获取{@link MessageMimeTypeMetadata}
     */
    private MessageMimeTypeMetadata getDataEncodingMetadata(RSocketCompositeMetadata compositeMetadata) {
        MessageMimeTypeMetadata dataEncodingMetadata = compositeMetadata.getMetadata(RSocketMimeType.MESSAGE_MIME_TYPE);
        if (dataEncodingMetadata == null) {
            return defaultMessageMimeTypeMetadata;
        } else {
            return dataEncodingMetadata;
        }
    }

    //getter

    /**
     * @return requester是否请求过服务
     */
    public boolean everConsumed() {
        return CollectionUtils.isNonEmpty(consumedServices);
    }

    public RSocketAppPrincipal getPrincipal() {
        return principal;
    }
}
