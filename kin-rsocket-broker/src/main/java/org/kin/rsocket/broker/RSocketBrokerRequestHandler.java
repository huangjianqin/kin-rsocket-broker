package org.kin.rsocket.broker;

import io.cloudevents.CloudEvent;
import io.netty.util.ReferenceCountUtil;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.exceptions.ApplicationErrorException;
import io.rsocket.exceptions.InvalidException;
import io.rsocket.frame.FrameType;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.core.AbstractRSocket;
import org.kin.rsocket.core.RSocketMimeType;
import org.kin.rsocket.core.event.CloudEventBus;
import org.kin.rsocket.core.event.UpstreamClusterChangedEvent;
import org.kin.rsocket.core.metadata.AppMetadata;
import org.kin.rsocket.core.metadata.BinaryRoutingMetadata;
import org.kin.rsocket.core.metadata.GSVRoutingMetadata;
import org.kin.rsocket.core.metadata.RSocketCompositeMetadata;
import org.kin.rsocket.core.utils.JSON;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * broker <- other broker
 * broker 处理其他 broker request
 *
 * @author huangjianqin
 * @date 2021/3/30
 */
final class RSocketBrokerRequestHandler extends AbstractRSocket {
    private static final Logger log = LoggerFactory.getLogger(RSocketBrokerRequestHandler.class);
    private final RSocketServiceManager serviceManager;
    private final RSocketFilterChain filterChain;
    /** broker requester app metadata */
    private final AppMetadata upstreamBrokerMetadata;

    public RSocketBrokerRequestHandler(RSocketServiceManager serviceManager,
                                       RSocketFilterChain filterChain,
                                       Payload setupPayload) {
        this.filterChain = filterChain;
        this.serviceManager = serviceManager;

        //解析composite metadata
        RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.from(setupPayload.metadata());
        if (compositeMetadata.contains(RSocketMimeType.APPLICATION)) {
            upstreamBrokerMetadata = compositeMetadata.getMetadata(RSocketMimeType.APPLICATION);
        } else {
            AppMetadata.Builder builder = AppMetadata.builder();
            builder.name("unknown peer broker");
            upstreamBrokerMetadata = builder.build();
        }
    }

    @Nonnull
    @Override
    public Mono<Payload> requestResponse(Payload payload) {
        try {
            BinaryRoutingMetadata binaryRoutingMetadata = BinaryRoutingMetadata.extract(payload.metadata());
            GSVRoutingMetadata gsvRoutingMetadata;
            if (binaryRoutingMetadata != null) {
                gsvRoutingMetadata = binaryRoutingMetadata.toGSVRoutingMetadata();
            } else {
                RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.from(payload.metadata());
                gsvRoutingMetadata = compositeMetadata.getMetadata(RSocketMimeType.ROUTING);
                if (gsvRoutingMetadata == null) {
                    throw new InvalidException("No Routing metadata");

                }
            }

            //request filters
            Mono<RSocket> destination;
            if (this.filterChain.isFiltersPresent()) {
                RSocketFilterContext filterContext = RSocketFilterContext.of(FrameType.REQUEST_RESPONSE, gsvRoutingMetadata, this.upstreamBrokerMetadata, payload);
                //filter可能会改变gsv metadata的数据, 影响路由结果
                destination = filterChain.filter(filterContext).then(findDestination(gsvRoutingMetadata));
            } else {
                destination = findDestination(gsvRoutingMetadata);
            }

            //call destination
            return destination.flatMap(rsocket -> {
                MetricsUtils.metrics(gsvRoutingMetadata, FrameType.REQUEST_RESPONSE.name());
                return rsocket.requestResponse(payload);
            });
        } catch (Exception e) {
            ReferenceCountUtil.safeRelease(payload);
            return Mono.error(e);
        }
    }

    @Nonnull
    @Override
    public Mono<Void> fireAndForget(Payload payload) {
        try {
            BinaryRoutingMetadata binaryRoutingMetadata = BinaryRoutingMetadata.extract(payload.metadata());
            GSVRoutingMetadata gsvRoutingMetadata;
            if (binaryRoutingMetadata != null) {
                gsvRoutingMetadata = binaryRoutingMetadata.toGSVRoutingMetadata();
            } else {
                RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.from(payload.metadata());
                gsvRoutingMetadata = compositeMetadata.getMetadata(RSocketMimeType.ROUTING);
                if (gsvRoutingMetadata == null) {
                    throw new InvalidException("No Routing metadata");

                }
            }

            //request filters
            Mono<RSocket> destination;
            if (this.filterChain.isFiltersPresent()) {
                RSocketFilterContext filterContext = RSocketFilterContext.of(FrameType.REQUEST_FNF, gsvRoutingMetadata, this.upstreamBrokerMetadata, payload);
                //filter可能会改变gsv metadata的数据, 影响路由结果
                destination = filterChain.filter(filterContext).then(findDestination(gsvRoutingMetadata));
            } else {
                destination = findDestination(gsvRoutingMetadata);
            }

            //call destination
            return destination.flatMap(rsocket -> {
                MetricsUtils.metrics(gsvRoutingMetadata, FrameType.REQUEST_FNF.name());
                return rsocket.fireAndForget(payload);
            });
        } catch (Exception e) {
            ReferenceCountUtil.safeRelease(payload);
            return Mono.error(e);
        }
    }

    @Nonnull
    @Override
    public Flux<Payload> requestStream(Payload payload) {
        try {
            BinaryRoutingMetadata binaryRoutingMetadata = BinaryRoutingMetadata.extract(payload.metadata());
            GSVRoutingMetadata gsvRoutingMetadata;
            if (binaryRoutingMetadata != null) {
                gsvRoutingMetadata = binaryRoutingMetadata.toGSVRoutingMetadata();
            } else {
                RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.from(payload.metadata());
                gsvRoutingMetadata = compositeMetadata.getMetadata(RSocketMimeType.ROUTING);
                if (gsvRoutingMetadata == null) {
                    throw new InvalidException("No Routing metadata");
                }
            }

            //request filters
            Mono<RSocket> destination;
            if (this.filterChain.isFiltersPresent()) {
                RSocketFilterContext filterContext = RSocketFilterContext.of(FrameType.REQUEST_STREAM, gsvRoutingMetadata, this.upstreamBrokerMetadata, payload);
                //filter可能会改变gsv metadata的数据, 影响路由结果
                destination = filterChain.filter(filterContext).then(findDestination(gsvRoutingMetadata));
            } else {
                destination = findDestination(gsvRoutingMetadata);
            }

            return destination.flatMapMany(rsocket -> {
                MetricsUtils.metrics(gsvRoutingMetadata, FrameType.REQUEST_STREAM.name());
                return rsocket.requestStream(payload);
            });
        } catch (Exception e) {
            ReferenceCountUtil.safeRelease(payload);
            return Flux.error(e);
        }
    }

    @Nonnull
    @Override
    public Flux<Payload> requestChannel(@Nonnull Publisher<Payload> payloads) {
        Flux<Payload> payloadsWithSignalRouting = (Flux<Payload>) payloads;
        //noinspection ConstantConditions
        return payloadsWithSignalRouting.switchOnFirst((signal, flux) -> requestChannel(signal.get(), flux));
    }

    private Flux<Payload> requestChannel(Payload signal, Flux<Payload> payloads) {
        try {
            BinaryRoutingMetadata binaryRoutingMetadata = BinaryRoutingMetadata.extract(signal.metadata());
            GSVRoutingMetadata gsvRoutingMetadata;
            if (binaryRoutingMetadata != null) {
                gsvRoutingMetadata = binaryRoutingMetadata.toGSVRoutingMetadata();
            } else {
                RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.from(signal.metadata());
                gsvRoutingMetadata = compositeMetadata.getMetadata(RSocketMimeType.ROUTING);
                if (gsvRoutingMetadata == null) {
                    throw new InvalidException("No Routing metadata");
                }
            }

            Mono<RSocket> destination = findDestination(gsvRoutingMetadata);
            return destination.flatMapMany(rsocket -> {
                MetricsUtils.metrics(gsvRoutingMetadata, FrameType.REQUEST_CHANNEL.name());
                return rsocket.requestChannel(payloads);
            });
        } catch (Exception e) {
            ReferenceCountUtil.safeRelease(signal);
            payloads.subscribe(ReferenceCountUtil::safeRelease);
            return Flux.error(e);
        }
    }

    @Nonnull
    @Override
    public Mono<Void> metadataPush(@Nonnull Payload payload) {
        try {
            if (payload.metadata().readableBytes() > 0) {
                CloudEvent cloudEvent = JSON.deserializeCloudEvent(payload.getMetadataUtf8());
                String type = cloudEvent.getType();
                if (UpstreamClusterChangedEvent.class.getName().equalsIgnoreCase(type)) {
                    //因为upstream broker是其他rsocket broker集群, 为了保证隔离, 不需要处理其他类型的事件广播
                    CloudEventBus.INSTANCE.postCloudEvent(cloudEvent);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse Cloud Event: ".concat(e.getMessage()), e);
        } finally {
            ReferenceCountUtil.safeRelease(payload);
        }
        return Mono.empty();
    }

    /**
     * 寻找目标服务provider instance
     */
    private Mono<RSocket> findDestination(GSVRoutingMetadata routingMetaData) {
        return Mono.create(sink -> {
            String gsv = routingMetaData.gsv();
            int serviceId = routingMetaData.serviceId();
            RSocketService targetService;
            RSocket rsocket = null;
            Exception error = null;

            String endpoint = routingMetaData.getEndpoint();
            if (StringUtils.isNotBlank(endpoint)) {
                targetService = serviceManager.getByEndpoint(endpoint, serviceId);
                if (targetService == null) {
                    error = new InvalidException(String.format("Service not found with endpoint '%s' '%s'", gsv, endpoint));
                }
            } else {
                targetService = serviceManager.routeByServiceId(serviceId);
                if (Objects.isNull(targetService)) {
                    error = new InvalidException(String.format("Service not found '%s'", gsv));
                }
            }

            if (targetService != null) {
                rsocket = targetService;
            }
            if (rsocket != null) {
                sink.success(rsocket);
            } else {
                sink.error(new ApplicationErrorException(String.format("Service not found '%s'", gsv), error));
            }
        });
    }
}