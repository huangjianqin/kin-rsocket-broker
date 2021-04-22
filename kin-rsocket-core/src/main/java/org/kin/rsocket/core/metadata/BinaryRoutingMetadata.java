package org.kin.rsocket.core.metadata;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.rsocket.metadata.CompositeMetadata;
import io.rsocket.metadata.WellKnownMimeType;
import io.rsocket.util.NumberUtils;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.core.RSocketMimeType;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * @author huangjianqin
 * @date 2021/4/23
 */
public class BinaryRoutingMetadata implements MetadataAware {
    private static final byte BINARY_ROUTING_MARK = (byte) (WellKnownMimeType.MESSAGE_RSOCKET_BINARY_ROUTING.getIdentifier() | 0x80);

    private int serviceId;
    private int handlerId;
    private boolean sticky;
    /** 相当于{@link GSVRoutingMetadata#genRoutingKey()} */
    private String routeKey;

    public static BinaryRoutingMetadata of(int serviceId, int handlerId, boolean sticky, String routeKey) {
        BinaryRoutingMetadata inst = new BinaryRoutingMetadata();
        inst.serviceId = serviceId;
        inst.handlerId = handlerId;
        inst.sticky = sticky;
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

    @Override
    public RSocketMimeType mimeType() {
        return RSocketMimeType.BinaryRouting;
    }

    @Override
    public ByteBuf getContent() {
        int capacity = 9;

        byte[] routeKeybytes = null;
        if (StringUtils.isNotBlank(routeKey)) {
            routeKeybytes = routeKey.getBytes(StandardCharsets.UTF_8);
            capacity = 9 + routeKeybytes.length;
        }
        ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer(capacity, capacity);
        byteBuf.writeInt(serviceId);
        byteBuf.writeInt(handlerId);
        byteBuf.writeBoolean(sticky);

        if (Objects.nonNull(routeKeybytes) && routeKeybytes.length > 0) {
            byteBuf.writeBytes(routeKeybytes);
        }
        return byteBuf;
    }

    @Override
    public void load(ByteBuf byteBuf) {
        this.serviceId = byteBuf.readInt();
        this.handlerId = byteBuf.readInt();
        this.sticky = byteBuf.readBoolean();
        int routeKeybytesLen = byteBuf.readableBytes();
        if (routeKeybytesLen > 0) {
            byte[] routeKeybytes = new byte[routeKeybytesLen];
            byteBuf.readBytes(routeKeybytes);
            routeKey = new String(routeKeybytes, StandardCharsets.UTF_8);
        }
    }

    /**
     * 带头部的bytes
     * {@link io.rsocket.metadata.CompositeMetadataCodec#decodeMimeAndContentBuffersSlices(ByteBuf, int, boolean)}
     * <p>
     * 该方法主要用于将{@link BinaryRoutingMetadata}放于{@link CompositeMetadata}首部, app可以直接取其首部即可解析serviceId和handlerId
     */
    public ByteBuf getHeaderAndContent() {
        int capacity = 13;
        byte[] routeKeybytes = null;
        if (StringUtils.isNotBlank(routeKey)) {
            routeKeybytes = routeKey.getBytes(StandardCharsets.UTF_8);
            capacity = 13 + routeKeybytes.length;
        }
        ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer(capacity, capacity);
        byteBuf.writeByte(BINARY_ROUTING_MARK);
        NumberUtils.encodeUnsignedMedium(byteBuf, capacity - 4);
        byteBuf.writeInt(serviceId);
        byteBuf.writeInt(handlerId);
        byteBuf.writeBoolean(sticky);
        if (Objects.nonNull(routeKeybytes) && routeKeybytes.length > 0) {
            byteBuf.writeBytes(routeKeybytes);
        }
        return byteBuf;
    }

    //getter
    public Integer getServiceId() {
        return serviceId;
    }

    public Integer getHandlerId() {
        return handlerId;
    }

    public boolean isSticky() {
        return sticky;
    }

    public String getRouteKey() {
        return routeKey;
    }
}