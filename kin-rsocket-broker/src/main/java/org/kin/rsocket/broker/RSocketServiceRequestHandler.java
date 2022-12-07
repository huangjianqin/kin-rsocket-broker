package org.kin.rsocket.broker;

import io.cloudevents.CloudEvent;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.util.ReferenceCountUtil;
import io.rsocket.ConnectionSetupPayload;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.exceptions.ApplicationErrorException;
import io.rsocket.exceptions.InvalidException;
import io.rsocket.frame.FrameType;
import io.rsocket.metadata.WellKnownMimeType;
import io.rsocket.util.ByteBufPayload;
import org.jctools.maps.NonBlockingHashMap;
import org.jctools.maps.NonBlockingHashSet;
import org.kin.framework.utils.CollectionUtils;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.auth.RSocketAppPrincipal;
import org.kin.rsocket.core.*;
import org.kin.rsocket.core.event.CloudEventBus;
import org.kin.rsocket.core.event.CloudEventSupport;
import org.kin.rsocket.core.metadata.*;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * service -> broker
 * broker处理service request
 *
 * @author huangjianqin
 * @date 2021/4/21
 */
public final class RSocketServiceRequestHandler extends RSocketRequestHandlerSupport {
    /** rsocket filter for requests */
    private final RSocketFilterChain filterChain;
    /** app metadata */
    private final AppMetadata appMetadata;
    /** authorized principal */
    private final RSocketAppPrincipal principal;
    /** sticky services, key -> serviceId, value -> instanceId */
    private final Map<Integer, Integer> stickyServices = new NonBlockingHashMap<>();
    /** upstream broker */
    private final UpstreamCluster upstreamBrokers;
    private final RSocketServiceManager serviceManager;
    private final RSocketServiceMeshInspector serviceMeshInspector;
    /** default消息编码类型 */
    private final MessageMimeTypeMetadata defaultMessageMimeTypeMetadata;
    /** 记录请求过的服务id */
    private final Set<String> consumedServices = new NonBlockingHashSet<>();

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
            this.defaultMessageMimeTypeMetadata = MessageMimeTypeMetadata.from(dataType);
        } else {
            //如果requester的RSocketConnector没有设置dataMimeType(), 则默认json
            this.defaultMessageMimeTypeMetadata = MessageMimeTypeMetadata.from(RSocketMimeType.defaultEncodingType());
        }

        this.appMetadata = appMetadata;
        this.principal = principal;
        this.serviceManager = serviceManager;
        this.serviceMeshInspector = serviceMeshInspector;
        this.filterChain = filterChain;
    }

    @SuppressWarnings("ConstantConditions")
    @Nonnull
    @Override
    public Mono<Payload> requestResponse(@Nonnull Payload payload) {
        String frameType = FrameType.REQUEST_RESPONSE.name();
        try {
            BinaryRoutingMetadata binaryRoutingMetadata = BinaryRoutingMetadata.extract(payload.metadata());

            GSVRoutingMetadata gsvRoutingMetadata;
            //为了兼容, 其余开发者rsocket broker client调用rsocket服务, 缺省部分信息, 也不会导致异常
            boolean encodingMetadataIncluded;
            MessageMimeTypeMetadata messageMimeTypeMetadata;
            if (Objects.isNull(binaryRoutingMetadata)) {
                //回退到取GSVRoutingMetadata
                RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.from(payload.metadata());
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
            }

            // broker local service call
            if (LocalRSocketServiceRegistry.INSTANCE.contains(gsvRoutingMetadata.handlerId())) {
                //app 与 broker通信使用rsocket connector设置的dataMimeType即可
                return localRequestResponse(gsvRoutingMetadata, defaultMessageMimeTypeMetadata, null, payload);
            }

            //request filters
            Mono<RSocket> destination;
            if (this.filterChain.isFiltersPresent()) {
                RSocketFilterContext filterContext = RSocketFilterContext.of(FrameType.REQUEST_RESPONSE, gsvRoutingMetadata, this.appMetadata, payload);
                //filter可能会改变gsv metadata的数据, 影响路由结果
                destination = filterChain.filter(filterContext).then(findDestination(gsvRoutingMetadata));
            } else {
                destination = findDestination(gsvRoutingMetadata);
            }

            //call destination
            return destination.flatMap(rsocket -> {
                recordServiceInvoke(gsvRoutingMetadata.gsv());
                if (Objects.isNull(binaryRoutingMetadata)) {
                    MetricsUtils.metrics(gsvRoutingMetadata, frameType);
                } else {
                    ServiceLocator serviceLocator = serviceManager.getServiceLocator(binaryRoutingMetadata.getServiceId());
                    MetricsUtils.metrics(serviceLocator, binaryRoutingMetadata.getHandler(), frameType);
                }

                if (encodingMetadataIncluded) {
                    return rsocket.requestResponse(payload);
                } else {
                    return rsocket.requestResponse(payloadWithDataEncoding(payload));
                }
            });
        } catch (Exception e) {
            error(failCallLog(frameType), e);
            ReferenceCountUtil.safeRelease(payload);
            return Mono.error(new InvalidException(failCallTips(frameType, e)));
        }
    }

    @SuppressWarnings("ConstantConditions")
    @Nonnull
    @Override
    public Mono<Void> fireAndForget(@Nonnull Payload payload) {
        String frameType = FrameType.REQUEST_FNF.name();
        try {
            BinaryRoutingMetadata binaryRoutingMetadata = BinaryRoutingMetadata.extract(payload.metadata());

            GSVRoutingMetadata gsvRoutingMetadata;
            //为了兼容, 其余开发者rsocket broker client调用rsocket服务, 缺省部分信息, 也不会导致异常
            boolean encodingMetadataIncluded;
            MessageMimeTypeMetadata messageMimeTypeMetadata;
            if (Objects.isNull(binaryRoutingMetadata)) {
                //回退到取GSVRoutingMetadata
                RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.from(payload.metadata());
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
            }

            // broker local service call
            if (LocalRSocketServiceRegistry.INSTANCE.contains(gsvRoutingMetadata.handlerId())) {
                //app 与 broker通信使用rsocket connector设置的dataMimeType即可
                return localFireAndForget(gsvRoutingMetadata, defaultMessageMimeTypeMetadata, payload);
            }

            //request filters
            Mono<RSocket> destination;
            if (this.filterChain.isFiltersPresent()) {
                RSocketFilterContext filterContext = RSocketFilterContext.of(FrameType.REQUEST_FNF, gsvRoutingMetadata, this.appMetadata, payload);
                //filter可能会改变gsv metadata的数据, 影响路由结果
                destination = filterChain.filter(filterContext).then(findDestination(gsvRoutingMetadata));
            } else {
                destination = findDestination(gsvRoutingMetadata);
            }

            //call destination
            return destination.flatMap(rsocket -> {
                recordServiceInvoke(gsvRoutingMetadata.gsv());
                if (Objects.isNull(binaryRoutingMetadata)) {
                    MetricsUtils.metrics(gsvRoutingMetadata, frameType);
                } else {
                    ServiceLocator serviceLocator = serviceManager.getServiceLocator(binaryRoutingMetadata.getServiceId());
                    MetricsUtils.metrics(serviceLocator, binaryRoutingMetadata.getHandler(), frameType);
                }
                if (encodingMetadataIncluded) {
                    return rsocket.fireAndForget(payload);
                } else {
                    return rsocket.fireAndForget(payloadWithDataEncoding(payload));
                }
            });
        } catch (Exception e) {
            error(failCallLog(frameType), e);
            ReferenceCountUtil.safeRelease(payload);
            return Mono.error(new InvalidException(failCallTips(frameType, e)));
        }

    }

    @SuppressWarnings("ConstantConditions")
    @Nonnull
    @Override
    public Flux<Payload> requestStream(@Nonnull Payload payload) {
        String frameType = FrameType.REQUEST_STREAM.name();
        try {
            BinaryRoutingMetadata binaryRoutingMetadata = BinaryRoutingMetadata.extract(payload.metadata());

            GSVRoutingMetadata gsvRoutingMetadata;
            //为了兼容, 其余开发者rsocket broker client调用rsocket服务, 缺省部分信息, 也不会导致异常
            boolean encodingMetadataIncluded;
            MessageMimeTypeMetadata messageMimeTypeMetadata;
            if (Objects.isNull(binaryRoutingMetadata)) {
                //回退到取GSVRoutingMetadata
                RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.from(payload.metadata());
                gsvRoutingMetadata = compositeMetadata.getMetadata(RSocketMimeType.ROUTING);
                if (Objects.isNull(gsvRoutingMetadata)) {
                    return Flux.error(new InvalidException("no routing metadata"));
                }
                messageMimeTypeMetadata = compositeMetadata.getMetadata(RSocketMimeType.MESSAGE_MIME_TYPE);
                encodingMetadataIncluded = Objects.nonNull(messageMimeTypeMetadata);
            } else {
                gsvRoutingMetadata = binaryRoutingMetadata.toGSVRoutingMetadata();
                //默认认为带了消息编码元数据
                encodingMetadataIncluded = true;
            }

            // broker local service call
            if (LocalRSocketServiceRegistry.INSTANCE.contains(gsvRoutingMetadata.handlerId())) {
                //app 与 broker通信使用rsocket connector设置的dataMimeType即可
                return localRequestStream(gsvRoutingMetadata, defaultMessageMimeTypeMetadata, null, payload);
            }

            //request filters
            Mono<RSocket> destination;
            if (this.filterChain.isFiltersPresent()) {
                RSocketFilterContext filterContext = RSocketFilterContext.of(FrameType.REQUEST_STREAM, gsvRoutingMetadata, this.appMetadata, payload);
                //filter可能会改变gsv metadata的数据, 影响路由结果
                destination = filterChain.filter(filterContext).then(findDestination(gsvRoutingMetadata));
            } else {
                destination = findDestination(gsvRoutingMetadata);
            }

            return destination.flatMapMany(rsocket -> {
                recordServiceInvoke(gsvRoutingMetadata.gsv());
                if (Objects.isNull(binaryRoutingMetadata)) {
                    MetricsUtils.metrics(gsvRoutingMetadata, frameType);
                } else {
                    ServiceLocator serviceLocator = serviceManager.getServiceLocator(binaryRoutingMetadata.getServiceId());
                    MetricsUtils.metrics(serviceLocator, binaryRoutingMetadata.getHandler(), frameType);
                }
                if (encodingMetadataIncluded) {
                    return rsocket.requestStream(payload);
                } else {
                    return rsocket.requestStream(payloadWithDataEncoding(payload));
                }
            });
        } catch (Exception e) {
            error(failCallLog(frameType), e);
            ReferenceCountUtil.safeRelease(payload);
            return Flux.error(new InvalidException(failCallTips(frameType, e)));
        }
    }

    private Flux<Payload> requestChannel(Payload signal, Flux<Payload> payloads) {
        String frameType = FrameType.REQUEST_CHANNEL.name();
        try {
            BinaryRoutingMetadata binaryRoutingMetadata = BinaryRoutingMetadata.extract(signal.metadata());

            GSVRoutingMetadata gsvRoutingMetadata;
            if (Objects.isNull(binaryRoutingMetadata)) {
                //回退到取GSVRoutingMetadata
                RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.from(signal.metadata());
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
                if (Objects.isNull(binaryRoutingMetadata)) {
                    MetricsUtils.metrics(gsvRoutingMetadata, frameType);
                } else {
                    ServiceLocator serviceLocator = serviceManager.getServiceLocator(binaryRoutingMetadata.getServiceId());
                    MetricsUtils.metrics(serviceLocator, binaryRoutingMetadata.getHandler(), frameType);
                }
                return rsocket.requestChannel(payloads);
            });
        } catch (Exception e) {
            error(failCallLog(frameType), e);
            ReferenceCountUtil.safeRelease(signal);
            payloads.subscribe(ReferenceCountUtil::safeRelease);
            return Flux.error(new InvalidException(failCallTips(frameType, e)));
        }
    }

    @Nonnull
    @Override
    public Flux<Payload> requestChannel(@Nonnull Publisher<Payload> payloads) {
        Flux<Payload> payloadsWithSignalRouting = (Flux<Payload>) payloads;
        return payloadsWithSignalRouting.switchOnFirst((signal, flux) -> requestChannel(signal.get(), flux));
    }

    @Nonnull
    @Override
    public Mono<Void> metadataPush(@Nonnull Payload payload) {
        try {
            if (payload.metadata().readableBytes() > 0) {
                CloudEvent cloudEvent = CloudEventSupport.extractCloudEventFromMetadata(payload);
                if (cloudEvent != null) {
                    /*
                     * 如果不是该responder对应的app uuid的cloud event, 则不处理
                     * 因为broker需要做拦截处理, 防止该app修改别的app
                     */
                    if (!appMetadata.getUuid().equalsIgnoreCase(cloudEvent.getSource().getHost())) {
                        return Mono.empty();
                    }
                    return Mono.fromRunnable(() -> CloudEventBus.INSTANCE.postCloudEvent(cloudEvent));
                }
            }
        } catch (Exception e) {
            error(String.format("Failed to parse Cloud Event: %s", e.getMessage()), e);
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
    private Payload payloadWithDataEncoding(Payload payload) {
        CompositeByteBuf compositeByteBuf = new CompositeByteBuf(PooledByteBufAllocator.DEFAULT, true, 2,
                payload.metadata(), toMimeAndContentBuffersSlices(defaultMessageMimeTypeMetadata));
        return ByteBufPayload.create(payload.data(), compositeByteBuf);
    }

    /**
     * 构建{@link io.rsocket.metadata.CompositeMetadata}entry的bytes
     * 详细编解码过程可以看{@link io.rsocket.metadata.CompositeMetadataCodec#decodeMimeAndContentBuffersSlices}
     */
    private static ByteBuf toMimeAndContentBuffersSlices(MessageMimeTypeMetadata metadata) {
        ByteBuf buf = PooledByteBufAllocator.DEFAULT.buffer(5, 5);
        //|0x80 代表第一个byte仅仅是mime type
        buf.writeByte((byte) (WellKnownMimeType.MESSAGE_RSOCKET_MIMETYPE.getIdentifier() | 0x80));
        //24bit代表内容长度
        buf.writeByte(0);
        buf.writeByte(0);
        buf.writeByte(1);
        //内容
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
            //错误消息提示时, 服务唯一标识显示的内容
            String serviceErrorMsg = StringUtils.isNotBlank(gsv) ? gsv : serviceId + "";
            RSocket rsocket = null;
            Exception error = null;
            //sticky session responder
            boolean sticky = routingMetaData.isSticky();
            RSocketEndpoint targetEndpoint = null;
            if (sticky) {
                // responder from sticky services
                targetEndpoint = findStickyServiceInstance(serviceId);
            }

            if (targetEndpoint != null) {
                rsocket = targetEndpoint;
            } else {
                String endpoint = routingMetaData.getEndpoint();
                if (StringUtils.isNotBlank(endpoint)) {
                    targetEndpoint = findDestinationWithEndpoint(endpoint, serviceId);
                    if (targetEndpoint == null) {
                        error = new InvalidException(String.format("Service not found with endpoint '%s' '%s'", serviceErrorMsg, endpoint));
                    }
                } else {
                    targetEndpoint = serviceManager.routeByServiceId(serviceId);
                    if (Objects.isNull(targetEndpoint)) {
                        error = new InvalidException(String.format("Service not found '%s'", serviceErrorMsg));
                    }
                }
                if (targetEndpoint != null) {
                    if (serviceMeshInspector.isAllowed(this.principal, serviceId, targetEndpoint.getPrincipal())) {
                        rsocket = targetEndpoint;
                        //save responder id if sticky
                        if (sticky) {
                            this.stickyServices.put(serviceId, targetEndpoint.getId());
                        }
                    } else {
                        error = new ApplicationErrorException(String.format("Service request not allowed '%s'", serviceErrorMsg));
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
                    sink.error(new ApplicationErrorException(String.format("Service not found '%s'", serviceErrorMsg), error));
                }
            }
        });
    }

    /**
     * 根据endpoint属性寻找target service instance
     */
    private RSocketEndpoint findDestinationWithEndpoint(String endpoint, Integer serviceId) {
        if (endpoint.startsWith("id:")) {
            return serviceManager.getByUUID(endpoint.substring(3));
        }
        int endpointHashCode = endpoint.hashCode();
        for (RSocketEndpoint rsocketEndpoint : serviceManager.getAllByServiceId(serviceId)) {
            if (rsocketEndpoint.getAppTagsHashCodeSet().contains(endpointHashCode)) {
                return rsocketEndpoint;
            }
        }
        return null;
    }

    /**
     * 寻找sticky service instance
     */
    private RSocketEndpoint findStickyServiceInstance(Integer serviceId) {
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

    /**
     * 路由失败信息
     */
    private String failCallLog(String frameType) {
        return String.format("handle %s request error", frameType);
    }

    /**
     * 路由失败返回提示信息
     */
    private String failCallTips(String frameType, Throwable throwable) {
        return failCallLog(frameType).concat(": ").concat(throwable.getMessage());
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

    public AppMetadata getAppMetadata() {
        return appMetadata;
    }
}
