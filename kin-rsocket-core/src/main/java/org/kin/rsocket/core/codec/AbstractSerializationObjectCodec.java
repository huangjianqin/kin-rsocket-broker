package org.kin.rsocket.core.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import org.kin.framework.utils.CollectionUtils;
import org.kin.framework.utils.ExceptionUtils;
import org.kin.serialization.Serialization;

/**
 * @author huangjianqin
 * @date 2021/3/26
 */
public abstract class AbstractSerializationObjectCodec implements ObjectCodec {
    /** 序列化接口实现类 */
    private final Serialization serialization;

    protected AbstractSerializationObjectCodec(Serialization serialization) {
        this.serialization = serialization;
    }

    /**
     * 序列化对象
     */
    private ByteBuf encodeObj(Object obj) {
        ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer(DEFAULT_BUFFER_SIZE);
        try {
            byteBuf.writeBytes(serialization.serialize(obj));
        } catch (Exception e) {
            ReferenceCountUtil.safeRelease(byteBuf);
            ExceptionUtils.throwExt(e);
        }
        return byteBuf;
    }

    /**
     * 序列化对象
     */
    private Object decodeObj(ByteBuf byteBuf, Class<?> targetClass) {
        return serialization.deserialize(byteBuf, targetClass);
    }

    @Override
    public ByteBuf encodeParams(Object[] args) throws ObjectCodecException {
        if (CollectionUtils.isEmpty(args)) {
            return Unpooled.EMPTY_BUFFER;
        } else if (args.length == 1) {
            return encodeObj(args[0]);
        } else {
            return encodeObj(new ObjectArray(args));
        }
    }

    @Override
    public Object decodeParams(ByteBuf data, Class<?>... targetClasses) throws ObjectCodecException {
        int len = targetClasses.length;
        if (len == 0) {
            return null;
        } else if (len == 1) {
            return decodeObj(data, targetClasses[0]);
        } else {
            return ((ObjectArray) decodeObj(data, ObjectArray.class)).getObjects();
        }
    }

    @Override
    public ByteBuf encodeResult(Object result) throws ObjectCodecException {
        return encodeObj(result);
    }

    @Override
    public Object decodeResult(ByteBuf data, Class<?> targetClass) throws ObjectCodecException {
        return decodeObj(data, targetClass);
    }
}
