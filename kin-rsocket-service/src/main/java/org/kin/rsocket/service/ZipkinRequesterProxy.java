package org.kin.rsocket.service;

import brave.Span;
import brave.Tracer;
import brave.propagation.TraceContext;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.rsocket.Payload;
import org.kin.rsocket.core.metadata.TracingMetadata;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author huangjianqin
 * @date 2021/8/19
 */
public class ZipkinRequesterProxy extends RequesterProxy {
    private final Tracer tracer;

    public ZipkinRequesterProxy(RSocketServiceReferenceBuilder<?> builder) {
        super(builder);
        tracer = builder.getTracing().tracer();
    }

    @Override
    protected Mono<Payload> requestResponse(ReactiveMethodMetadata methodMetadata, ByteBuf compositeMetadataBytes, ByteBuf paramBodyBytes) {
        return Mono.deferContextual(context -> {
            TraceContext traceContext = context.getOrDefault(TraceContext.class, null);
            if (traceContext != null) {
                CompositeByteBuf newCompositeMetadata = new CompositeByteBuf(PooledByteBufAllocator.DEFAULT, true,
                        2, compositeMetadataBytes, tracingMetadata(traceContext).getContent());
                Span span = tracer.newChild(traceContext);
                return super.requestResponse(methodMetadata, newCompositeMetadata, paramBodyBytes)
                        .doOnError(span::error)
                        .doOnSuccess(payload -> span.finish());
            }
            return super.requestResponse(methodMetadata, compositeMetadataBytes, paramBodyBytes);
        });
    }

    @Override
    protected Mono<Void> fireAndForget(ReactiveMethodMetadata methodMetadata, ByteBuf compositeMetadataBytes, ByteBuf paramBodyBytes) {
        return Mono.deferContextual(context -> {
            TraceContext traceContext = context.getOrDefault(TraceContext.class, null);
            if (traceContext != null) {
                CompositeByteBuf newCompositeMetadata = new CompositeByteBuf(PooledByteBufAllocator.DEFAULT, true,
                        2, compositeMetadataBytes, tracingMetadata(traceContext).getContent());
                Span span = tracer.newChild(traceContext);
                return super.fireAndForget(methodMetadata, newCompositeMetadata, paramBodyBytes)
                        .doOnError(span::error)
                        .doOnSuccess(payload -> span.finish());
            }
            return super.fireAndForget(methodMetadata, compositeMetadataBytes, paramBodyBytes);
        });
    }

    @Override
    protected Flux<Payload> requestStream(ReactiveMethodMetadata methodMetadata, ByteBuf compositeMetadataBytes, ByteBuf paramBodyBytes) {
        return Flux.deferContextual(context -> {
            TraceContext traceContext = context.getOrDefault(TraceContext.class, null);
            if (traceContext != null) {
                CompositeByteBuf newCompositeMetadata = new CompositeByteBuf(PooledByteBufAllocator.DEFAULT, true,
                        2, compositeMetadataBytes, tracingMetadata(traceContext).getContent());
                Span span = tracer.newChild(traceContext);
                return super.requestStream(methodMetadata, newCompositeMetadata, paramBodyBytes)
                        .doOnError(span::error)
                        .doOnComplete(span::finish);
            }
            return super.requestStream(methodMetadata, compositeMetadataBytes, paramBodyBytes);
        });
    }

    /**
     * 创建{@link TracingMetadata}
     */
    private TracingMetadata tracingMetadata(TraceContext traceContext) {
        return TracingMetadata.of(
                traceContext.traceIdHigh(),
                traceContext.traceId(),
                traceContext.spanId(),
                traceContext.parentId(),
                true, false);
    }
}
