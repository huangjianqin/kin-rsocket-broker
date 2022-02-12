package org.kin.rsocket.core.metadata;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import org.kin.rsocket.core.RSocketMimeType;

/**
 * @author huangjianqin
 * @date 2021/3/24
 */
public final class CacheControlMetadata implements MetadataAware {
    /** expired timestamp */
    private long expiredAt;

    public static CacheControlMetadata of(long expiredAt) {
        CacheControlMetadata metadata = new CacheControlMetadata();
        metadata.expiredAt = expiredAt;
        return metadata;
    }

    public static CacheControlMetadata of(ByteBuf content) {
        CacheControlMetadata metadata = new CacheControlMetadata();
        metadata.load(content);
        return metadata;
    }

    private CacheControlMetadata() {
    }

    @Override
    public RSocketMimeType mimeType() {
        return RSocketMimeType.CACHE_CONTROL;
    }

    @Override
    public ByteBuf getContent() {
        ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer(8);
        byteBuf.writeLong(expiredAt);
        return byteBuf;
    }

    @Override
    public void load(ByteBuf byteBuf) {
        this.expiredAt = byteBuf.readLong();
    }

    //getter

    public long getExpiredAt() {
        return expiredAt;
    }
}

