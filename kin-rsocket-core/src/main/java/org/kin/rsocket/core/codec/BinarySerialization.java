package org.kin.rsocket.core.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.kin.kinrpc.serialization.Serialization;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author huangjianqin
 * @date 2021/3/25
 */
public class BinarySerialization implements Serialization {
    /** 空bytes */
    private static final byte[] EMPTY = new byte[0];

    @Override
    public byte[] serialize(Object target) throws IOException {
        if (target != null) {
            ByteBuf byteBuf;
            if (target instanceof ByteBuf) {
                byteBuf = (ByteBuf) target;
            } else if (target instanceof ByteBuffer) {
                byteBuf = Unpooled.wrappedBuffer((ByteBuffer) target);
            } else if (target instanceof byte[]) {
                return (byte[]) target;
            } else {
                return EMPTY;
            }

            byte[] bytes = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(bytes);
            return bytes;
        }

        return EMPTY;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T deserialize(byte[] bytes, Class<T> targetClass) throws IOException, ClassNotFoundException {
        if (bytes != null && bytes.length > 0) {
            if (targetClass.equals(ByteBuf.class)) {
                return (T) Unpooled.wrappedBuffer(bytes);
            } else if (targetClass.equals(ByteBuffer.class)) {
                return (T) ByteBuffer.wrap(bytes);
            } else if (targetClass.equals(byte[].class)) {
                return (T) bytes;
            }
        }
        return null;
    }

    @Override
    public int type() {
        return 9;
    }
}
