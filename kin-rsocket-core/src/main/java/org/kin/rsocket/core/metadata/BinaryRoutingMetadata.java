package org.kin.rsocket.core.metadata;

import com.google.common.base.Preconditions;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.rsocket.metadata.CompositeMetadata;
import io.rsocket.metadata.WellKnownMimeType;
import io.rsocket.util.NumberUtils;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.core.RSocketMimeType;
import org.kin.transport.netty.utils.VarIntUtils;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 仅仅用于broker快速route, 在目标rsocket service端还是需要{@link GSVRoutingMetadata}
 * broker仅仅需要读取少量整形即可进行route, 而不用解析大量string, 提高broker一丢丢性能
 *
 * @author huangjianqin
 * @date 2021/4/23
 */
public final class BinaryRoutingMetadata implements MetadataAware {
    private static final byte BINARY_ROUTING_MARK = (byte) (WellKnownMimeType.MESSAGE_RSOCKET_BINARY_ROUTING.getIdentifier() | 0x80);
    /** 空flags */
    private static final boolean[] EMPTY_FLAGS = new boolean[0];
    /** sticky 在flags数值里面的 plot */
    private static final byte STICKY_PLOT = 0;

    /** 仅仅用于调试, 相当于{@link GSVRoutingMetadata#genRoutingKey()} */
    private transient String routeKey;
    /** 参考{@link org.kin.rsocket.core.ServiceLocator} */
    private int serviceId;
    /** 参考{@link GSVRoutingMetadata#handlerId()} */
    private int handlerId;
    /** 标签 */
    private boolean[] flags = EMPTY_FLAGS;
    /** route key里面的handler, 用于metrics */
    private String handler;

    public static BinaryRoutingMetadata of(GSVRoutingMetadata gsvRoutingMetadata) {
        return of(gsvRoutingMetadata.genRoutingKey(), gsvRoutingMetadata.serviceId(), gsvRoutingMetadata.handlerId(), gsvRoutingMetadata.getHandler(), gsvRoutingMetadata.isSticky());
    }

    public static BinaryRoutingMetadata of(String routeKey, int serviceId, int handlerId, String handler, boolean... flags) {
        Preconditions.checkArgument(StringUtils.isNotBlank(routeKey), "routeKey must be not blank");
        BinaryRoutingMetadata inst = new BinaryRoutingMetadata();
        inst.routeKey = routeKey;
        inst.serviceId = serviceId;
        inst.handlerId = handlerId;
        inst.handler = handler;
        if (Objects.nonNull(flags) && flags.length > 0) {
            inst.flags = flags;
        }
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
        return RSocketMimeType.BINARY_ROUTING;
    }

    @Override
    public ByteBuf getContent() {
        ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer(64);
        //利用protobuf压缩整形机制, 减少bytes体积, 提高性能
        VarIntUtils.writeRawVarInt32(byteBuf, serviceId);
        VarIntUtils.writeRawVarInt32(byteBuf, handlerId);
        VarIntUtils.writeRawVarInt32(byteBuf, flags.length);
        for (boolean flag : flags) {
            byteBuf.writeBoolean(flag);
        }
        //write hanlder
        byte[] handlerBytes = handler.getBytes(StandardCharsets.UTF_8);
        VarIntUtils.writeRawVarInt32(byteBuf, handlerBytes.length);
        byteBuf.writeBytes(handlerBytes);
        return byteBuf;
    }

    @Override
    public void load(ByteBuf byteBuf) {
        serviceId = VarIntUtils.readRawVarInt32(byteBuf);
        handlerId = VarIntUtils.readRawVarInt32(byteBuf);
        int flagSize = VarIntUtils.readRawVarInt32(byteBuf);
        flags = new boolean[flagSize];
        for (int i = 0; i < flagSize; i++) {
            flags[i] = byteBuf.readBoolean();
        }

        int handlerBytesLen = VarIntUtils.readRawVarInt32(byteBuf);
        byte[] handlerBytes = new byte[handlerBytesLen];
        byteBuf.readBytes(handlerBytes);
        handler = new String(handlerBytes, StandardCharsets.UTF_8);
    }

    /**
     * 带头部的bytes
     * {@link io.rsocket.metadata.CompositeMetadataCodec#decodeMimeAndContentBuffersSlices(ByteBuf, int, boolean)}
     * <p>
     * 该方法主要用于将{@link BinaryRoutingMetadata}放于{@link CompositeMetadata}首部, app可以直接取其首部即可解析serviceId和handlerId
     */
    public ByteBuf getHeaderAndContent() {
        ByteBuf content = getContent();
        int capacity = 4 + content.readableBytes();

        ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer(capacity, capacity);
        byteBuf.writeByte(BINARY_ROUTING_MARK);
        NumberUtils.encodeUnsignedMedium(byteBuf, capacity - 4);
        byteBuf.writeBytes(content);
        return byteBuf;
    }

    /**
     * 转换成{@link GSVRoutingMetadata}
     */
    public GSVRoutingMetadata toGSVRoutingMetadata() {
        return GSVRoutingMetadata.of(this);
    }

    //getter
    public String getRouteKey() {
        return routeKey;
    }

    public int getServiceId() {
        return serviceId;
    }

    public int getHandlerId() {
        return handlerId;
    }

    public String getHandler() {
        return handler;
    }

    public boolean[] getFlags() {
        return flags;
    }

    private boolean getFlag(int plot) {
        if (flags.length <= 0) {
            return false;
        }

        return flags[plot];
    }

    public boolean isSticky() {
        return getFlag(STICKY_PLOT);
    }
}