package org.kin.rsocket.core.metadata;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import org.kin.framework.utils.ClassUtils;
import org.kin.rsocket.core.RSocketMimeType;

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
        //todo 目前先手动注册, 后续考虑优化自动注册关联
        builder.put(RSocketMimeType.Application, AppMetadata.class);
        builder.put(RSocketMimeType.CacheControl, CacheControlMetadata.class);
        builder.put(RSocketMimeType.ServiceRegistry, ServiceRegistryMetadata.class);
        builder.put(RSocketMimeType.BearerToken, BearerTokenMetadata.class);
        builder.put(RSocketMimeType.Routing, GSVRoutingMetadata.class);
        builder.put(RSocketMimeType.MessageMimeType, MessageMimeTypeMetadata.class);
        builder.put(RSocketMimeType.MessageAcceptMimeTypes, MessageAcceptMimeTypesMetadata.class);
        builder.put(RSocketMimeType.CompositeMetadata, RSocketCompositeMetadata.class);
        builder.put(RSocketMimeType.MessageTags, MessageTagsMetadata.class);
        builder.put(RSocketMimeType.MessageOrigin, OriginMetadata.class);

        //todo Tracing和BinaryRouting mime type未处理

        TYPE_2_METADATA_CLASS = builder.build();
    }

    @SuppressWarnings("unchecked")
    public static <T extends MetadataAware> T instance(RSocketMimeType mimeType) {
        Class<? extends MetadataAware> claxx = TYPE_2_METADATA_CLASS.get(mimeType);
        return Objects.isNull(claxx) ? null : (T) ClassUtils.instance(claxx);
    }
}
