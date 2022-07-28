package org.kin.rsocket.service;

import brave.Span;
import brave.Tracer;
import brave.propagation.TraceContext;
import io.netty.util.ReferenceCountUtil;
import io.rsocket.ConnectionSetupPayload;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.exceptions.InvalidException;
import org.kin.rsocket.core.RSocketAppContext;
import org.kin.rsocket.core.RSocketMimeType;
import org.kin.rsocket.core.RSocketResponderHandlerSupport;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.CloudEventSupport;
import org.kin.rsocket.core.metadata.*;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * service <- broker/peer service
 * service处理broker/peer service request
 *
 * @author huangjianqin
 * @date 2021/3/28
 */
@SuppressWarnings({"rawtypes", "unchecked"})
final class RSocketResponderHandler extends RSocketResponderHandlerSupport {
    /** requester from peer */
    private final RSocket requester;
    /**
     * 默认数据编码类型, 从requester setup payload解析而来
     * 返回数据给requester时, 如果没有带数据编码类型, 则使用默认的编码类型进行编码
     */
    private final MessageMimeTypeMetadata defaultMessageMimeTypeMetadata;
    /** combo onClose from responder and requester */
    private final Mono<Void> comboOnClose;
    /** zipkin */
    private final Tracer tracer;

    RSocketResponderHandler(RSocket requester, ConnectionSetupPayload setupPayload, Tracer tracer) {
        this.requester = requester;
        this.comboOnClose = Mono.firstWithSignal(super.onClose(), requester.onClose());

        //requester默认data编码类型
        RSocketMimeType dataMimeType = RSocketMimeType.defaultEncodingType();
        //解析composite metadata
        RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.from(setupPayload.metadata());
        if (compositeMetadata.contains(RSocketMimeType.APPLICATION)) {
            AppMetadata appMetadata = compositeMetadata.getMetadata(RSocketMimeType.APPLICATION);
            //from remote requester
            if (!appMetadata.getUuid().equals(RSocketAppContext.ID)) {
                RSocketMimeType requesterDataMimeType = RSocketMimeType.getByType(setupPayload.dataMimeType());
                if (Objects.nonNull(requesterDataMimeType)) {
                    dataMimeType = requesterDataMimeType;
                }
            }
        }

        this.defaultMessageMimeTypeMetadata = MessageMimeTypeMetadata.from(dataMimeType);
        this.tracer = tracer;
    }

    /**
     * No Routing metadata异常的{@link Mono}实例
     */
    private Mono noRoutingDataErrorMono() {
        return Mono.error(new InvalidException("No Routing metadata"));
    }

    /**
     * No encoding metadata异常的{@link Mono}实例
     */
    private Mono noEncodingDataErrorMono() {
        return Mono.error(new InvalidException("No encoding metadata"));
    }

    /**
     * No Routing metadata异常的{@link Flux}实例
     */
    private Flux noRoutingDataErrorFlux() {
        return Flux.error(new InvalidException("No Routing metadata"));
    }

    /**
     * No encoding metadata异常的{@link Flux}实例
     */
    private Flux noEncodingDataErrorFlux() {
        return Flux.error(new InvalidException("No encoding metadata"));
    }

    @Nonnull
    @Override
    public Mono<Payload> requestResponse(Payload payload) {
        RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.from(payload.metadata());
        GSVRoutingMetadata routingMetaData = getGsvRoutingMetadata(compositeMetadata);
        if (routingMetaData == null) {
            ReferenceCountUtil.safeRelease(payload);
            return noRoutingDataErrorMono();
        }
        MessageMimeTypeMetadata dataEncodingMetadata = getDataEncodingMetadata(compositeMetadata);
        if (dataEncodingMetadata == null) {
            ReferenceCountUtil.safeRelease(payload);
            return noEncodingDataErrorMono();
        }
        Mono<Payload> payloadMono = localRequestResponse(routingMetaData, dataEncodingMetadata, compositeMetadata.getMetadata(RSocketMimeType.MESSAGE_ACCEPT_MIME_TYPES), payload);
        return injectTraceContext(payloadMono, compositeMetadata);
    }

    @Nonnull
    @Override
    public Mono<Void> fireAndForget(Payload payload) {
        RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.from(payload.metadata());
        GSVRoutingMetadata routingMetaData = getGsvRoutingMetadata(compositeMetadata);
        if (routingMetaData == null) {
            ReferenceCountUtil.safeRelease(payload);
            return noRoutingDataErrorMono();
        }
        MessageMimeTypeMetadata dataEncodingMetadata = getDataEncodingMetadata(compositeMetadata);
        if (dataEncodingMetadata == null) {
            ReferenceCountUtil.safeRelease(payload);
            return noEncodingDataErrorMono();
        }
        Mono<Void> voidMono = localFireAndForget(routingMetaData, dataEncodingMetadata, payload);
        return injectTraceContext(voidMono, compositeMetadata);
    }

    @Nonnull
    @Override
    public Flux<Payload> requestStream(Payload payload) {
        RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.from(payload.metadata());
        GSVRoutingMetadata routingMetaData = getGsvRoutingMetadata(compositeMetadata);
        if (routingMetaData == null) {
            ReferenceCountUtil.safeRelease(payload);
            return noRoutingDataErrorFlux();
        }
        MessageMimeTypeMetadata dataEncodingMetadata = getDataEncodingMetadata(compositeMetadata);
        if (dataEncodingMetadata == null) {
            ReferenceCountUtil.safeRelease(payload);
            return noEncodingDataErrorFlux();
        }
        Flux<Payload> payloadFlux = localRequestStream(routingMetaData, dataEncodingMetadata, compositeMetadata.getMetadata(RSocketMimeType.MESSAGE_ACCEPT_MIME_TYPES), payload);
        return injectTraceContext(payloadFlux, compositeMetadata);
    }

    private Flux<Payload> requestChannel(Payload signal, Publisher<Payload> payloads) {
        RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.from(signal.metadata());
        GSVRoutingMetadata routingMetaData = getGsvRoutingMetadata(compositeMetadata);
        if (routingMetaData == null) {
            ReferenceCountUtil.safeRelease(signal);
            return noRoutingDataErrorFlux();
        }
        MessageMimeTypeMetadata dataEncodingMetadata = getDataEncodingMetadata(compositeMetadata);
        if (dataEncodingMetadata == null) {
            ReferenceCountUtil.safeRelease(signal);
            return noEncodingDataErrorFlux();
        }

        return localRequestChannel(routingMetaData, dataEncodingMetadata,
                compositeMetadata.getMetadata(RSocketMimeType.MESSAGE_ACCEPT_MIME_TYPES), signal,
                ((Flux<Payload>) payloads).skip(1));
    }

    @SuppressWarnings("ConstantConditions")
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
                CloudEventData<?> cloudEvent = CloudEventSupport.extractCloudEventsFromMetadata(payload);
                if (cloudEvent != null) {
                    return Mono.fromRunnable(() -> RSocketAppContext.CLOUD_EVENT_SINK.tryEmitNext(cloudEvent));
                }
            }
        } catch (Exception e) {
            error("Failed to parse Cloud Event:  " + e.getMessage(), e);
        } finally {
            ReferenceCountUtil.safeRelease(payload);
        }
        return Mono.empty();
    }

    @Nonnull
    @Override
    public Mono<Void> onClose() {
        return this.comboOnClose;
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
     * 解析并获取{@link GSVRoutingMetadata}
     */
    private GSVRoutingMetadata getGsvRoutingMetadata(RSocketCompositeMetadata compositeMetadata) {
        return compositeMetadata.getMetadata(RSocketMimeType.ROUTING);
    }

    /**
     * 根据{@link TracingMetadata}构建{@link TraceContext}
     */
    private TraceContext constructTraceContext(TracingMetadata tracingMetadata) {
        return TraceContext.newBuilder()
                .parentId(tracingMetadata.getParentId())
                .spanId(tracingMetadata.getSpanId())
                .traceIdHigh(tracingMetadata.getTraceIdHigh())
                .traceId(tracingMetadata.getTraceId())
                .build();
    }

    /**
     * 给{@link Mono}context写入{@link TraceContext}
     */
    private <T> Mono<T> injectTraceContext(Mono<T> payloadMono, RSocketCompositeMetadata compositeMetadata) {
        if (Objects.nonNull(tracer)) {
            TracingMetadata tracingMetadata = compositeMetadata.getMetadata(RSocketMimeType.TRACING);
            if (Objects.nonNull(tracingMetadata)) {
                TraceContext traceContext = constructTraceContext(tracingMetadata);
                Span span = tracer.newChild(traceContext);
                return payloadMono
                        .doOnError(span::error)
                        .doOnSuccess(payload -> span.finish())
                        .contextWrite(Context.of(TraceContext.class, traceContext));
            }
        }
        return payloadMono;
    }

    /**
     * 给{@link Flux}context写入{@link TraceContext}
     */
    private Flux<Payload> injectTraceContext(Flux<Payload> payloadFlux, RSocketCompositeMetadata compositeMetadata) {
        if (Objects.nonNull(tracer)) {
            TracingMetadata tracingMetadata = compositeMetadata.getMetadata(RSocketMimeType.TRACING);
            if (Objects.nonNull(tracingMetadata)) {
                TraceContext traceContext = constructTraceContext(tracingMetadata);
                Span span = tracer.newChild(traceContext);
                return payloadFlux
                        .doOnError(span::error)
                        .doOnComplete(span::finish)
                        .contextWrite(Context.of(TraceContext.class, traceContext));
            }
        }
        return payloadFlux;
    }
}
