package org.kin.rsocket.core.metadata;

import brave.propagation.TraceContext;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.rsocket.metadata.TracingMetadataCodec;
import org.kin.rsocket.core.RSocketMimeType;

/**
 * Tracing metadata: https://github.com/rsocket/rsocket/blob/master/Extensions/Tracing-Zipkin.md
 *
 * @author huangjianqin
 * @date 2021/8/19
 */
public class TracingMetadata implements MetadataAware {
    private long traceIdHigh;
    /**
     * Unique 8-byte identifier for a trace, set on all spans within it.
     */
    private long traceId;
    /**
     * Indicates if the parent's {@link #spanId} or if this the root span in a trace.
     */
    private boolean hasParentId;
    /**
     * Returns the parent's {@link #spanId} where zero implies absent.
     */
    private long parentId;
    /**
     * Unique 8-byte identifier of this span within a trace.
     *
     * <p>A span is uniquely identified in storage by ({@linkplain #traceId}, {@linkplain #spanId}).
     */
    private long spanId;
    /**
     * Includes that there is sampling information and no trace IDs.
     */
    private boolean isEmpty;
    private boolean isNotSampled;
    /**
     * Indicates that trace IDs should be accepted for tracing.
     */
    private boolean isSampled;
    /**
     * Indicates that trace IDs should be force traced.
     */
    private boolean isDebug;

    public static TracingMetadata zipkin(TraceContext traceContext) {
        return TracingMetadata.from(
                traceContext.traceIdHigh(),
                traceContext.traceId(),
                traceContext.spanId(),
                traceContext.parentId(),
                true, false);
    }

    public static TracingMetadata from(long traceIdHigh, long traceId, long spanId, long parentId, boolean sampled, boolean debug) {
        TracingMetadata metadata = new TracingMetadata();
        metadata.traceIdHigh = traceIdHigh;
        metadata.traceId = traceId;
        metadata.spanId = spanId;
        metadata.parentId = parentId;
        if (parentId != 0) {
            metadata.hasParentId = true;
        }
        metadata.isSampled = sampled;
        metadata.isDebug = debug;
        if (!(sampled || debug)) {
            metadata.isNotSampled = true;
        }
        return metadata;
    }

    public static TracingMetadata from(ByteBuf content) {
        TracingMetadata temp = new TracingMetadata();
        temp.load(content);
        return temp;
    }

    private TracingMetadata() {
    }

    /**
     * Indicated that sampling decision is present. If {@code false} means that decision is unknown
     * and says explicitly that {@link #isDebug()} and {@link #isSampled()} also returns {@code
     * false}.
     */
    public boolean isDecided() {
        return isNotSampled || isDebug || isSampled;
    }


    @Override
    public RSocketMimeType mimeType() {
        return RSocketMimeType.TRACING;
    }

    @Override
    public ByteBuf getContent() {
        if (this.hasParentId) {
            return TracingMetadataCodec.encode128(PooledByteBufAllocator.DEFAULT, this.traceIdHigh, this.traceId, this.spanId, this.parentId, toFlags());
        } else {
            return TracingMetadataCodec.encode128(PooledByteBufAllocator.DEFAULT, this.traceIdHigh, this.traceId, this.spanId, toFlags());
        }
    }

    @Override
    public void load(ByteBuf byteBuf) {
        io.rsocket.metadata.TracingMetadata tempTracing = TracingMetadataCodec.decode(byteBuf);
        this.traceIdHigh = tempTracing.traceIdHigh();
        this.traceId = tempTracing.traceId();
        this.spanId = tempTracing.spanId();
        this.parentId = tempTracing.parentId();
        this.hasParentId = tempTracing.hasParent();
        this.isSampled = tempTracing.isSampled();
        this.isDebug = tempTracing.isDebug();
        this.isEmpty = tempTracing.isEmpty();
    }

    private TracingMetadataCodec.Flags toFlags() {
        if (this.isSampled) {
            return TracingMetadataCodec.Flags.SAMPLE;
        } else if (this.isDebug) {
            return TracingMetadataCodec.Flags.DEBUG;
        } else if (!this.isDecided()) {
            return TracingMetadataCodec.Flags.UNDECIDED;
        } else {
            return TracingMetadataCodec.Flags.NOT_SAMPLE;
        }
    }

    //setter && getter
    public long getTraceIdHigh() {
        return traceIdHigh;
    }

    public long getTraceId() {
        return traceId;
    }

    public boolean isHasParentId() {
        return hasParentId;
    }

    public long getParentId() {
        return parentId;
    }

    public long getSpanId() {
        return spanId;
    }

    public boolean isEmpty() {
        return isEmpty;
    }

    public boolean isNotSampled() {
        return isNotSampled;
    }

    public boolean isSampled() {
        return isSampled;
    }

    public boolean isDebug() {
        return isDebug;
    }
}
