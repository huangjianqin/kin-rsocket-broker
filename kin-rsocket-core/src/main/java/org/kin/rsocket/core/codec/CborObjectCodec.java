package org.kin.rsocket.core.codec;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
public class CborObjectCodec implements ObjectCodec {
    private static final ObjectMapper PARSER = new ObjectMapper(new CBORFactory());

    static {
        PARSER.findAndRegisterModules();
        //允许json中含有指定对象未包含的字段
        PARSER.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        //允许序列化空对象
        PARSER.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        //不序列化默认值, 0,false,[],{}等等, 减少json长度
        PARSER.setDefaultPropertyInclusion(JsonInclude.Include.NON_DEFAULT);
        //只认field, 那些get set is开头的方法不生成字段
        PARSER.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        PARSER.setVisibility(PropertyAccessor.SETTER, JsonAutoDetect.Visibility.NONE);
        PARSER.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE);
        PARSER.setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE);
    }

    @Override
    public RSocketMimeType mimeType() {
        return RSocketMimeType.CBOR;
    }

    @Override
    public ByteBuf encodeParams(Object[] args) throws ObjectCodecException {
        if (CollectionUtils.isNonEmpty(args)) {
            ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer(DEFAULT_BUFFER_SIZE);
            try {
                ByteBufOutputStream bos = new ByteBufOutputStream(byteBuf);
                PARSER.writeValue((OutputStream) bos, args);
                return byteBuf;
            } catch (Exception e) {
                ReferenceCountUtil.safeRelease(byteBuf);
                ExceptionUtils.throwExt(e);
            }
        }

        return Unpooled.EMPTY_BUFFER;
    }

    @Override
    public Object decodeParams(ByteBuf data, Class<?>... targetClasses) throws ObjectCodecException {
        if (data.readableBytes() > 0 && targetClasses != null && targetClasses.length > 0) {
            try {
                return PARSER.readValue((InputStream) new ByteBufInputStream(data), Object[].class);
            } catch (Exception e) {
                ExceptionUtils.throwExt(e);
            }
        }

        return null;
    }

    @Override
    public ByteBuf encodeResult(Object result) throws ObjectCodecException {
        if (result != null) {
            ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer(DEFAULT_BUFFER_SIZE);
            try {
                ByteBufOutputStream bos = new ByteBufOutputStream(byteBuf);
                PARSER.writeValue((OutputStream) bos, result);
                return byteBuf;
            } catch (Exception e) {
                ReferenceCountUtil.safeRelease(byteBuf);
                ExceptionUtils.throwExt(e);
            }
        }
        return Unpooled.EMPTY_BUFFER;
    }

    @Override
    public Object decodeResult(ByteBuf data, Class<?> targetClass) throws ObjectCodecException {
        if (data.readableBytes() > 0 && targetClass != null) {
            try {
                return PARSER.readValue((InputStream) new ByteBufInputStream(data), targetClass);
            } catch (Exception e) {
                ExceptionUtils.throwExt(e);
            }
        }
        return null;
    }
}
