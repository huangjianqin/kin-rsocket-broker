package org.kin.rsocket.core.codec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.netty.buffer.*;
import io.netty.util.ReferenceCountUtil;
import org.kin.framework.utils.CollectionUtils;
import org.kin.framework.utils.ExceptionUtils;
import org.kin.rsocket.core.RSocketMimeType;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author huangjianqin
 * @date 2021/4/20
 */
public class CborCodec implements Codec {
    private static final ObjectMapper MAPPER = new ObjectMapper(new CBORFactory());

    @Override
    public RSocketMimeType mimeType() {
        return RSocketMimeType.CBOR;
    }

    @Override
    public ByteBuf encodeParams(Object[] args) throws CodecException {
        if (CollectionUtils.isNonEmpty(args)) {
            ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer(DEFAULT_BUFFER_SIZE);
            try {
                ByteBufOutputStream bos = new ByteBufOutputStream(byteBuf);
                MAPPER.writeValue((OutputStream) bos, args);
                return byteBuf;
            } catch (Exception e) {
                ReferenceCountUtil.safeRelease(byteBuf);
                ExceptionUtils.throwExt(e);
            }
        }

        return Unpooled.EMPTY_BUFFER;
    }

    @Override
    public Object decodeParams(ByteBuf data, Class<?>... targetClasses) throws CodecException {
        if (data.readableBytes() > 0 && targetClasses != null && targetClasses.length > 0) {
            try {
                return MAPPER.readValue((InputStream) new ByteBufInputStream(data), Object[].class);
            } catch (Exception e) {
                ExceptionUtils.throwExt(e);
            }
        }

        return null;
    }

    @Override
    public ByteBuf encodeResult(Object result) throws CodecException {
        if (result != null) {
            ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer(DEFAULT_BUFFER_SIZE);
            try {
                ByteBufOutputStream bos = new ByteBufOutputStream(byteBuf);
                MAPPER.writeValue((OutputStream) bos, result);
                return byteBuf;
            } catch (Exception e) {
                ReferenceCountUtil.safeRelease(byteBuf);
                ExceptionUtils.throwExt(e);
            }
        }
        return Unpooled.EMPTY_BUFFER;
    }

    @Override
    public Object decodeResult(ByteBuf data, Class<?> targetClass) throws CodecException {
        if (data.readableBytes() > 0 && targetClass != null) {
            try {
                return MAPPER.readValue((InputStream) new ByteBufInputStream(data), targetClass);
            } catch (Exception e) {
                ExceptionUtils.throwExt(e);
            }
        }
        return null;
    }
}
