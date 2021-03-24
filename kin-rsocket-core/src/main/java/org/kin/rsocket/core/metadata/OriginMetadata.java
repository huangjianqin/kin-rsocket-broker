package org.kin.rsocket.core.metadata;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * @author huangjianqin
 * @date 2021/3/25
 */
public class OriginMetadata implements MetadataAware {
    /** todo */
    private URI origin;

    public static OriginMetadata of(URI origin) {
        OriginMetadata metadata = new OriginMetadata();
        metadata.origin = origin;
        return metadata;
    }

    public static OriginMetadata of(ByteBuf content) {
        OriginMetadata metadata = new OriginMetadata();
        metadata.load(content);
        return metadata;
    }

    @Override
    public RSocketMimeType mimeType() {
        return RSocketMimeType.MessageOrigin;
    }

    @Override
    public ByteBuf getContent() {
        byte[] bytes = this.origin.toString().getBytes(StandardCharsets.UTF_8);
        ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer(bytes.length, bytes.length);
        byteBuf.writeBytes(bytes);
        return byteBuf;
    }

    @Override
    public void load(ByteBuf byteBuf) {
        String text = byteBuf.toString(StandardCharsets.UTF_8);
        this.origin = URI.create(text);
    }

    //setter && getter
    public URI getOrigin() {
        return origin;
    }

    public void setOrigin(URI origin) {
        this.origin = origin;
    }
}
