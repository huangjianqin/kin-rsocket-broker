package org.kin.rsocket.core.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.kin.framework.utils.CollectionUtils;
import org.kin.rsocket.core.RSocketMimeType;
import org.kin.rsocket.core.utils.JSON;

import java.util.Arrays;

/**
 * @author huangjianqin
 * @date 2021/3/26
 */
public class JsonCodec implements Codec {
    @Override
    public RSocketMimeType mimeType() {
        return RSocketMimeType.Json;
    }

    @Override
    public ByteBuf encodeParams(Object[] args) throws CodecException {
        if (CollectionUtils.isEmpty(args)) {
            return Unpooled.EMPTY_BUFFER;
        }
        return JSON.writeByteBuf(args);
    }

    @Override
    public Object decodeParams(ByteBuf data, Class<?>... targetClasses) throws CodecException {
        if (data.readableBytes() > 0 && !CollectionUtils.isEmpty(targetClasses)) {
            try {
                return JSON.readJsonArray(data, targetClasses);
            } catch (Exception e) {
                throw new CodecException(String.format("Failed to decode data bytebuf to %s", Arrays.toString(targetClasses)), e);
            }
        }
        return null;
    }

    @Override
    public ByteBuf encodeResult(Object result) throws CodecException {
        if (result == null) {
            return Unpooled.EMPTY_BUFFER;
        }
        return JSON.writeByteBuf(result);
    }

    @Override
    public Object decodeResult(ByteBuf data, Class<?> targetClass) throws CodecException {
        if (data.readableBytes() > 0 && targetClass != null) {
            return JSON.read(data, targetClass);
        }
        return null;
    }
}
