package org.kin.rsocket.core.metadata;

import com.google.common.base.Preconditions;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.rsocket.metadata.CompositeMetadata;
import io.rsocket.metadata.WellKnownMimeType;
import io.rsocket.util.NumberUtils;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.core.RSocketMimeType;

import java.nio.charset.StandardCharsets;

/**
 * @author huangjianqin
 * @date 2021/4/23
 */
public final class BinaryRoutingMetadata implements MetadataAware {
    private static final byte BINARY_ROUTING_MARK = (byte) (WellKnownMimeType.MESSAGE_RSOCKET_BINARY_ROUTING.getIdentifier() | 0x80);
    /** 相当于{@link GSVRoutingMetadata#genRoutingKey()} */
    private String routeKey;

    public static BinaryRoutingMetadata of(String routeKey) {
        Preconditions.checkArgument(StringUtils.isNotBlank(routeKey), "routeKey must be not blank");
        BinaryRoutingMetadata inst = new BinaryRoutingMetadata();
        inst.routeKey = routeKey;
        return inst;
    }

    public static BinaryRoutingMetadata of(ByteBuf content) {
        BinaryRoutingMetadata temp = new BinaryRoutingMetadata();
        temp.load(content);
        return temp;
    }

    /**
     * 取{@link CompositeMetadata}第一个entry的metadata
     */
    public static BinaryRoutingMetadata extract(ByteBuf compositeByteBuf) {
        long typeAndService = compositeByteBuf.getLong(0);
        if ((typeAndService >> 56) == BINARY_ROUTING_MARK) {
            int metadataContentLen = (int) (typeAndService >> 32) & 0x00FFFFFF;
            return BinaryRoutingMetadata.of(compositeByteBuf.slice(4, metadataContentLen));
        }
        return null;
    }

    private BinaryRoutingMetadata() {
    }

    @Override
    public RSocketMimeType mimeType() {
        return RSocketMimeType.BinaryRouting;
    }

    @Override
    public ByteBuf getContent() {
        byte[] routeKeybytes = routeKey.getBytes(StandardCharsets.UTF_8);
        ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer(routeKeybytes.length, routeKeybytes.length);
        byteBuf.writeBytes(routeKeybytes);
        return byteBuf;
    }

    @Override
    public void load(ByteBuf byteBuf) {
        int routeKeybytesLen = byteBuf.readableBytes();
        byte[] routeKeybytes = new byte[routeKeybytesLen];
        byteBuf.readBytes(routeKeybytes);
        routeKey = new String(routeKeybytes, StandardCharsets.UTF_8);
    }

    /**
     * 带头部的bytes
     * {@link io.rsocket.metadata.CompositeMetadataCodec#decodeMimeAndContentBuffersSlices(ByteBuf, int, boolean)}
     * <p>
     * 该方法主要用于将{@link BinaryRoutingMetadata}放于{@link CompositeMetadata}首部, app可以直接取其首部即可解析serviceId和handlerId
     */
    public ByteBuf getHeaderAndContent() {
        byte[] routeKeybytes = routeKey.getBytes(StandardCharsets.UTF_8);
        int capacity = 4 + routeKeybytes.length;

        ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer(capacity, capacity);
        byteBuf.writeByte(BINARY_ROUTING_MARK);
        NumberUtils.encodeUnsignedMedium(byteBuf, capacity - 4);
        byteBuf.writeBytes(routeKeybytes);
        return byteBuf;
    }

    /**
     * 转换成{@link GSVRoutingMetadata}
     */
    public GSVRoutingMetadata toGSVRoutingMetadata() {
        return GSVRoutingMetadata.of(routeKey);
    }

    //getter
    public String getRouteKey() {
        return routeKey;
    }
}