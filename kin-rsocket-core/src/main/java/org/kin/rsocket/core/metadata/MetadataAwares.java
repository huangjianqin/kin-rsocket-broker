package org.kin.rsocket.core.metadata;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import io.netty.buffer.ByteBuf;
import org.kin.rsocket.core.RSocketMimeType;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * @author huangjianqin
 * @date 2021/3/25
 */
final class MetadataAwares {
    private MetadataAwares() {
    }

    /** key -> {@link RSocketMimeType} value -> {@link MetadataAware}实现类对应class */
    private static final BiMap<RSocketMimeType, Class<? extends MetadataAware>> TYPE_2_METADATA_CLASS;

    static {
        ImmutableBiMap.Builder<RSocketMimeType, Class<? extends MetadataAware>> builder = ImmutableBiMap.builder();
        //todo 优化:目前先手动注册, 后续考虑优化自动注册关联
        builder.put(RSocketMimeType.Application, AppMetadata.class);
        builder.put(RSocketMimeType.CacheControl, CacheControlMetadata.class);
        builder.put(RSocketMimeType.ServiceRegistry, ServiceRegistryMetadata.class);
        builder.put(RSocketMimeType.BearerToken, BearerTokenMetadata.class);
        builder.put(RSocketMimeType.Routing, GSVRoutingMetadata.class);
        builder.put(RSocketMimeType.BinaryRouting, BinaryRoutingMetadata.class);
        builder.put(RSocketMimeType.MessageMimeType, MessageMimeTypeMetadata.class);
        builder.put(RSocketMimeType.MessageAcceptMimeTypes, MessageAcceptMimeTypesMetadata.class);
        builder.put(RSocketMimeType.CompositeMetadata, RSocketCompositeMetadata.class);
        builder.put(RSocketMimeType.MessageTags, MessageTagsMetadata.class);
        builder.put(RSocketMimeType.MessageOrigin, OriginMetadata.class);

        //todo 优化:Tracing未处理

        TYPE_2_METADATA_CLASS = builder.build();
    }

    @SuppressWarnings("unchecked")
    public static <T extends MetadataAware> T instance(RSocketMimeType mimeType, ByteBuf bytes) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class<? extends MetadataAware> claxx = TYPE_2_METADATA_CLASS.get(mimeType);
        if (Objects.isNull(claxx)) {
            return null;
        }

        /**
         * 取{@link MetadataAware}实现类中of(ByteBuf)静态方法, 作为构造{@link MetadataAware}实例的入口
         */
        Method parseBytesMethod = claxx.getDeclaredMethod("of", ByteBuf.class);
        if (!parseBytesMethod.isAccessible()) {
            parseBytesMethod.setAccessible(true);
        }
        return (T) parseBytesMethod.invoke(null, bytes);
    }
}
