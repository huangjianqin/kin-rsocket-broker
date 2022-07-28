package org.kin.rsocket.core.metadata;

import io.netty.buffer.ByteBuf;
import org.kin.rsocket.core.RSocketMimeType;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * @author huangjianqin
 * @date 2021/3/25
 */
final class MetadataAwares {
    private MetadataAwares() {
    }

    /** key -> {@link RSocketMimeType}, value -> {@link MetadataAwareMetadata} */
    private static final Map<RSocketMimeType, MetadataAwareMetadata> TYPE_2_METADATA_AWARE_METADATA;

    static {
        Map<RSocketMimeType, MetadataAwareMetadata> map = new HashMap<>(16);

        map.put(RSocketMimeType.APPLICATION, new MetadataAwareMetadata(AppMetadata.class, AppMetadata::from));
        map.put(RSocketMimeType.CACHE_CONTROL, new MetadataAwareMetadata(CacheControlMetadata.class, CacheControlMetadata::from));
        map.put(RSocketMimeType.SERVICE_REGISTRY, new MetadataAwareMetadata(RSocketServiceRegistryMetadata.class, RSocketServiceRegistryMetadata::from));
        map.put(RSocketMimeType.BEARER_TOKEN, new MetadataAwareMetadata(BearerTokenMetadata.class, BearerTokenMetadata::from));
        map.put(RSocketMimeType.ROUTING, new MetadataAwareMetadata(GSVRoutingMetadata.class, GSVRoutingMetadata::from));
        map.put(RSocketMimeType.BINARY_ROUTING, new MetadataAwareMetadata(BinaryRoutingMetadata.class, BinaryRoutingMetadata::from));
        map.put(RSocketMimeType.MESSAGE_MIME_TYPE, new MetadataAwareMetadata(MessageMimeTypeMetadata.class, MessageMimeTypeMetadata::from));
        map.put(RSocketMimeType.MESSAGE_ACCEPT_MIME_TYPES, new MetadataAwareMetadata(MessageAcceptMimeTypesMetadata.class, MessageAcceptMimeTypesMetadata::from));
        map.put(RSocketMimeType.COMPOSITE_METADATA, new MetadataAwareMetadata(RSocketCompositeMetadata.class, RSocketCompositeMetadata::from));
        map.put(RSocketMimeType.MESSAGE_TAGS, new MetadataAwareMetadata(MessageTagsMetadata.class, MessageTagsMetadata::from));
        map.put(RSocketMimeType.MESSAGE_ORIGIN, new MetadataAwareMetadata(OriginMetadata.class, OriginMetadata::from));

        TYPE_2_METADATA_AWARE_METADATA = map;
    }

    @SuppressWarnings("unchecked")
    static <T extends MetadataAware> T instance(RSocketMimeType mimeType, ByteBuf bytes) {
        if (Objects.isNull(mimeType)) {
            throw new IllegalArgumentException("arg 'mimeType' is null");
        }

        MetadataAwareMetadata metadataAwareMetadata = TYPE_2_METADATA_AWARE_METADATA.get(mimeType);
        if (Objects.isNull(metadataAwareMetadata)) {
            throw new IllegalStateException("unable to find MetadataAware implement class where its mime type is " + mimeType);
        }

        Class<? extends MetadataAware> metadataAwareClass = metadataAwareMetadata.getMetadataAwareClass();
        //MetadataAware实现类中of(ByteBuf)静态方法, 作为构造MetadataAware实例的入口
        Function<ByteBuf, ? extends MetadataAware> parseBytesFunc = metadataAwareMetadata.getParseBytesFunc();
        T metadataAware = (T) parseBytesFunc.apply(bytes);
        //二次校验
        if (!mimeType.equals(metadataAware.mimeType())) {
            throw new IllegalStateException(String.format("%s class mime type isn't %s", metadataAwareClass.getName(), mimeType));
        }
        return metadataAware;
    }

    /**
     * 动态绑定{@link RSocketMimeType}与其{@link MetadataAware}实现类的关联
     */
    public static void bind(RSocketMimeType mimeType, MetadataAwareMetadata metadataAwareMetadata) {
        TYPE_2_METADATA_AWARE_METADATA.put(mimeType, metadataAwareMetadata);
    }
}
