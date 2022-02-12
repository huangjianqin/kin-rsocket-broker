package org.kin.rsocket.core.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.kin.framework.utils.CollectionUtils;
import org.kin.serialization.Serialization;

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
        ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer(DEFAULT_BUFFER_SIZE);
        byteBuf.writeBytes(serialization.serialize(obj));
        return byteBuf;
    }

    /**
     * 序列化对象
     */
    private Object decodeObj(ByteBuf byteBuf, Class<?> targetClass) {
        return serialization.deserialize(byteBuf, targetClass);
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
        // TODO: 2022/1/16  pb是否支持反序列化object[], 如果实在不行就在README写注释
        return decodeObj(data, targetClasses[0]);
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
