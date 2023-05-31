package org.kin.rsocket.core.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.kin.framework.utils.CollectionUtils;
import org.kin.rsocket.core.RSocketMimeType;

import java.nio.ByteBuffer;

/**
 * 仅支持序列化|反序列化1个bytes
 *
 * @author huangjianqin
 * @date 2021/3/25
 */
public class BinaryObjectCodec implements ObjectCodec {
    @Override
    public RSocketMimeType mimeType() {
        return RSocketMimeType.BINARY;
    }

    @Override
    public ByteBuf encodeParams(Object[] args) throws ObjectCodecException {
        if (CollectionUtils.isEmpty(args)) {
            return Unpooled.EMPTY_BUFFER;
        }
        return encodeResult(args[0]);
    }

    @Override
    public Object decodeParams(ByteBuf data, Class<?>... targetClasses) throws ObjectCodecException {
        if (data.readableBytes() > 0 && CollectionUtils.isNonEmpty(targetClasses)) {
            return decodeResult(data, targetClasses[0]);
        }
        return null;
    }

    @Override
    public ByteBuf encodeResult(Object result) throws ObjectCodecException {
        if (result != null) {
            if (result instanceof ByteBuf) {
                return (ByteBuf) result;
            } else if (result instanceof ByteBuffer) {
                ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer(DEFAULT_BUFFER_SIZE);
                byteBuf.writeBytes(((ByteBuffer) result));
                return byteBuf;
            } else if (result instanceof byte[]) {
                ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer(DEFAULT_BUFFER_SIZE);
                byteBuf.writeBytes(((byte[]) result));
                return byteBuf;
            } else {
                return Unpooled.EMPTY_BUFFER;
            }
        }

        return Unpooled.EMPTY_BUFFER;
    }

    @Override
    public Object decodeResult(ByteBuf data, Class<?> targetClass) throws ObjectCodecException {
        if (data.readableBytes() > 0 && targetClass != null) {
            if (targetClass.equals(ByteBuf.class)) {
                return data;
            } else if (targetClass.equals(ByteBuffer.class)) {
                return data.nioBuffer();
            } else if (targetClass.equals(byte[].class)) {
                int length = data.readableBytes();
                byte[] content = new byte[length];
                data.readBytes(content);
                return content;
            }
        }
        return null;
    }
}
