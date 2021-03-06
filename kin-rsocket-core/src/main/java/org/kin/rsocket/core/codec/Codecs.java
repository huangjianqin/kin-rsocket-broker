package org.kin.rsocket.core.codec;

import com.google.common.collect.ImmutableMap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import org.kin.framework.utils.CollectionUtils;
import org.kin.rsocket.core.RSocketAppContext;
import org.kin.rsocket.core.RSocketMimeType;
import org.kin.rsocket.core.metadata.MessageMimeTypeMetadata;
import org.kin.rsocket.core.metadata.RSocketCompositeMetadata;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * rsocket mime type codec
 *
 * @author huangjianqin
 * @date 2021/3/25
 */
public final class Codecs {
    /** 单例 */
    public static final Codecs INSTANCE = new Codecs();

    /** key -> rsocket mime type, value -> {@link Codec} */
    private final Map<RSocketMimeType, Codec> mimeType2Codec;
    /**
     * default composite metadata ByteBuf for message mime types
     * 可以省去创建mimetype元数据的耗时, 相当于MessageMimeTypeMetadata元数据池
     */
    private final Map<RSocketMimeType, ByteBuf> compositeMetadataForMimeTypes;

    private Codecs() {
        ImmutableMap.Builder<RSocketMimeType, Codec> codecBuilder = ImmutableMap.builder();
        ImmutableMap.Builder<RSocketMimeType, ByteBuf> mimeTypeMetadataBytesBuilder = ImmutableMap.builder();
        //通过spi加载codec实现类
        for (Codec codec : RSocketAppContext.LOADER.getExtensions(Codec.class)) {
            RSocketMimeType mimeType = codec.mimeType();
            codecBuilder.put(codec.mimeType(), codec);

            RSocketCompositeMetadata resultCompositeMetadata = RSocketCompositeMetadata.of(MessageMimeTypeMetadata.of(mimeType));
            ByteBuf compositeMetadataContent = resultCompositeMetadata.getContent();
            mimeTypeMetadataBytesBuilder.put(mimeType, Unpooled.copiedBuffer(compositeMetadataContent));
            ReferenceCountUtil.safeRelease(compositeMetadataContent);
        }

        mimeType2Codec = codecBuilder.build();
        compositeMetadataForMimeTypes = mimeTypeMetadataBytesBuilder.build();
    }

    /**
     * 根据指定mime type, 对服务接口参数进行编码
     */
    public ByteBuf encodeParams(Object[] args, RSocketMimeType mimeType) {
        if (CollectionUtils.isEmpty(args)) {
            return Unpooled.EMPTY_BUFFER;
        }
        try {
            Codec codec = mimeType2Codec.get(mimeType);
            return codec.encodeParams(args);
        } catch (Exception e) {
            handleCodecException(e);
            return Unpooled.EMPTY_BUFFER;
        }
    }

    /**
     * 根据指定mime type, 对服务接口参数进行解码
     */
    public Object decodeParams(RSocketMimeType mimeType, ByteBuf data, Class<?>... targetClasses) {
        try {
            if (data == null || data.readableBytes() == 0) {
                return null;
            }
            return mimeType2Codec.get(mimeType).decodeParams(data, targetClasses);
        } catch (Exception e) {
            handleCodecException(e);
            return null;
        }
    }

    /**
     * 根据指定mime type, 对服务接口返回值进行编码
     */
    public ByteBuf encodeResult(Object result, RSocketMimeType mimeType) {
        try {
            return mimeType2Codec.get(mimeType).encodeResult(result);
        } catch (Exception e) {
            handleCodecException(e);
            return Unpooled.EMPTY_BUFFER;
        }
    }

    /**
     * 根据指定mime type, 对服务接口返回值进行解码
     */
    public Object decodeResult(RSocketMimeType mimeType, ByteBuf data, Class<?> targetClass) {
        try {
            if (data == null || data.readableBytes() == 0) {
                return null;
            }

            if (targetClass == ByteBuffer.class) {
                return data.nioBuffer();
            } else if (targetClass == ByteBuf.class) {
                return data;
            }
            return mimeType2Codec.get(mimeType).decodeResult(data, targetClass);
        } catch (Exception e) {
            handleCodecException(e);
            return null;
        }
    }

    /**
     * 获取默认的MessageMimeTypeMetadata元数据
     */
    public ByteBuf getDefaultCompositeMetadataByteBuf(RSocketMimeType mimeType) {
        return this.compositeMetadataForMimeTypes.get(mimeType).retainedDuplicate();
    }

    /**
     * 处理encode和decode期间发生的异常
     */
    private void handleCodecException(Exception e) {
        if (e instanceof CodecException) {
            throw (CodecException) e;
        } else {
            throw new CodecException("codec encounter error", e);
        }
    }
}
