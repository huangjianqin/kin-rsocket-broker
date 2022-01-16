package org.kin.rsocket.core.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.kin.framework.utils.CollectionUtils;
import org.kin.rsocket.core.RSocketMimeType;

import java.nio.charset.StandardCharsets;

/**
 * @author huangjianqin
 * @date 2021/3/25
 */
public class TextCodec implements Codec {
    @Override
    public RSocketMimeType mimeType() {
        return RSocketMimeType.TEXT;
    }

    @Override
    public ByteBuf encodeParams(Object[] args) throws CodecException {
        if (CollectionUtils.isEmpty(args)) {
            return Unpooled.EMPTY_BUFFER;
        }
        ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer(256);
        byteBuf.writeBytes(stringToBytes(args[0]));
        return byteBuf;
    }

    @Override
    public Object decodeParams(ByteBuf data, Class<?>... targetClasses) throws CodecException {
        if (data.readableBytes() > 0) {
            return data.toString(StandardCharsets.UTF_8);
        }
        return null;
    }

    @Override
    public ByteBuf encodeResult(Object result) throws CodecException {
        if (result == null) {
            return Unpooled.EMPTY_BUFFER;
        }
        ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer(256);
        byteBuf.writeBytes(stringToBytes(result));
        return byteBuf;
    }

    @Override
    public Object decodeResult(ByteBuf data, Class<?> targetClass) throws CodecException {
        if (data.readableBytes() > 0 && targetClass != null) {
            return data.toString(StandardCharsets.UTF_8);
        }
        return null;
    }

    /**
     * string转换成bytes
     */
    private byte[] stringToBytes(Object obj) throws CodecException {
        try {
            return obj.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new CodecException(e.getMessage());
        }
    }
}
