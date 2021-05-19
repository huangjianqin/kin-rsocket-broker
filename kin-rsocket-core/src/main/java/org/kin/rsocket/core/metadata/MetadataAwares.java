package org.kin.rsocket.core.metadata;

import io.netty.buffer.ByteBuf;
import org.kin.rsocket.core.RSocketMimeType;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author huangjianqin
 * @date 2021/3/25
 */
final class MetadataAwares {
    private MetadataAwares() {
    }

    /** key -> {@link RSocketMimeType} value -> {@link MetadataAware}实现类对应class */
    private static final Map<RSocketMimeType, Class<? extends MetadataAware>> TYPE_2_METADATA_CLASS;

    static {
        Map<RSocketMimeType, Class<? extends MetadataAware>> map = new HashMap<>(16);

        map.put(RSocketMimeType.Application, AppMetadata.class);
        map.put(RSocketMimeType.CacheControl, CacheControlMetadata.class);
        map.put(RSocketMimeType.ServiceRegistry, RSocketServiceRegistryMetadata.class);
        map.put(RSocketMimeType.BearerToken, BearerTokenMetadata.class);
        map.put(RSocketMimeType.Routing, GSVRoutingMetadata.class);
        map.put(RSocketMimeType.BinaryRouting, BinaryRoutingMetadata.class);
        map.put(RSocketMimeType.MessageMimeType, MessageMimeTypeMetadata.class);
        map.put(RSocketMimeType.MessageAcceptMimeTypes, MessageAcceptMimeTypesMetadata.class);
        map.put(RSocketMimeType.CompositeMetadata, RSocketCompositeMetadata.class);
        map.put(RSocketMimeType.MessageTags, MessageTagsMetadata.class);
        map.put(RSocketMimeType.MessageOrigin, OriginMetadata.class);

        //todo 优化:Tracing未处理

        TYPE_2_METADATA_CLASS = map;
    }

    @SuppressWarnings("unchecked")
    static <T extends MetadataAware> T instance(RSocketMimeType mimeType, ByteBuf bytes) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        if (Objects.isNull(mimeType)) {
            throw new IllegalArgumentException("arg 'mimeType' is null");
        }

        Class<? extends MetadataAware> claxx = TYPE_2_METADATA_CLASS.get(mimeType);
        if (Objects.isNull(claxx)) {
            throw new IllegalStateException("unable to find MetadataAware implement class where its mime type is " + mimeType);
        }

        /**
         * 取{@link MetadataAware}实现类中of(ByteBuf)静态方法, 作为构造{@link MetadataAware}实例的入口
         */
        Method parseBytesMethod = claxx.getDeclaredMethod("of", ByteBuf.class);
        if (!parseBytesMethod.isAccessible()) {
            parseBytesMethod.setAccessible(true);
        }

        T metadataAware = (T) parseBytesMethod.invoke(null, bytes);
        //校验
        if (!mimeType.equals(metadataAware.mimeType())) {
            throw new IllegalStateException(String.format("%s class mime tpye isn't %s", claxx.getName(), mimeType));
        }
        return metadataAware;
    }

    /**
     * 动态绑定{@link RSocketMimeType}与其{@link MetadataAware}实现类的关联
     */
    public static void bind(RSocketMimeType mimeType, Class<? extends MetadataAware> metadataAwareClass) {
        TYPE_2_METADATA_CLASS.put(mimeType, metadataAwareClass);
    }
}
