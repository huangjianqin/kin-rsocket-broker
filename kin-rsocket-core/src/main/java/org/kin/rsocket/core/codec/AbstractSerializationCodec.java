package org.kin.rsocket.core.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.kin.framework.utils.CollectionUtils;
import org.kin.framework.utils.ExceptionUtils;
import org.kin.kinrpc.serialization.Serialization;

import java.io.IOException;

/**
 * @author huangjianqin
 * @date 2021/3/26
 */
public abstract class AbstractSerializationCodec implements Codec {
    /** 序列化接口实现类 */
    private final Serialization serialization;

    protected AbstractSerializationCodec(Serialization serialization) {
        this.serialization = serialization;
    }

    /**
     * 序列化对象
     */
    private ByteBuf encodeObj(Object obj) {
        try {
            return Unpooled.wrappedBuffer(serialization.serialize(obj));
        } catch (IOException e) {
            ExceptionUtils.throwExt(e);
        }

        return Unpooled.EMPTY_BUFFER;
    }

    /**
     * 序列化对象
     */
    private Object decodeObj(ByteBuf data, Class<?> targetClass) {
        if (data.readableBytes() > 0) {
            try {
                byte[] bytes = new byte[data.readableBytes()];
                data.readBytes(bytes);
                return serialization.deserialize(bytes, targetClass);
            } catch (IOException | ClassNotFoundException e) {
                ExceptionUtils.throwExt(e);
            }
        }

        return null;
    }

    @Override
    public ByteBuf encodeParams(Object[] args) throws CodecException {
        if (CollectionUtils.isEmpty(args)) {
            return Unpooled.EMPTY_BUFFER;
        }
        return encodeObj(args);
    }

    @Override
    public Object decodeParams(ByteBuf data, Class<?>... targetClasses) throws CodecException {
        return decodeObj(data, Object[].class);
    }

    @Override
    public ByteBuf encodeResult(Object result) throws CodecException {
        return encodeObj(result);
    }

    @Override
    public Object decodeResult(ByteBuf data, Class<?> targetClass) throws CodecException {
        return decodeObj(data, targetClass);
    }
}
