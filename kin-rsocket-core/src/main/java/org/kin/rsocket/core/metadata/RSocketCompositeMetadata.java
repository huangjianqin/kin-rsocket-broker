package org.kin.rsocket.core.metadata;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.rsocket.metadata.CompositeMetadata;
import io.rsocket.metadata.CompositeMetadataCodec;
import io.rsocket.metadata.WellKnownMimeType;
import org.kin.framework.utils.ExceptionUtils;
import org.kin.rsocket.core.RSocketMimeType;

import java.util.*;

import static io.rsocket.metadata.WellKnownMimeType.UNPARSEABLE_MIME_TYPE;

/**
 * @author huangjianqin
 * @date 2021/3/25
 */
public final class RSocketCompositeMetadata implements MetadataAware {
    /**
     * key -> mime type, value -> bytes,
     * size会>= type2Metadata.size(), 因为存在一些未知的mime type, 不知道如何转换成class, 所以以bytes形式存在
     */
    private final Map<String, ByteBuf> metadataBytesStore = new HashMap<>(4);
    /** key -> {@link RSocketMimeType}, value -> 对应mime type 的{@link MetadataAware}实现类 */
    private final Map<RSocketMimeType, MetadataAware> metadataStore = new HashMap<>(4);

    public static RSocketCompositeMetadata of(ByteBuf content) {
        RSocketCompositeMetadata metadata = new RSocketCompositeMetadata();
        if (content.isReadable()) {
            metadata.load(content);
        }
        return metadata;
    }

    public static RSocketCompositeMetadata of(MetadataAware... metadatas) {
        return of(Arrays.asList(metadatas));
    }

    public static RSocketCompositeMetadata of(Collection<MetadataAware> metadatas) {
        RSocketCompositeMetadata metadata = new RSocketCompositeMetadata();
        for (MetadataAware childMetadata : metadatas) {
            metadata.addMetadata(childMetadata);
        }
        return metadata;
    }

    private RSocketCompositeMetadata() {
    }

    @Override
    public RSocketMimeType mimeType() {
        return RSocketMimeType.COMPOSITE_METADATA;
    }

    @Override
    public ByteBuf getContent() {
        //假设平均每个metadata size=256
        CompositeByteBuf compositeByteBuf = PooledByteBufAllocator.DEFAULT.compositeBuffer(metadataBytesStore.size() * 256);
        for (Map.Entry<String, ByteBuf> entry : metadataBytesStore.entrySet()) {
            WellKnownMimeType wellKnownMimeType = WellKnownMimeType.fromString(entry.getKey());
            if (wellKnownMimeType != UNPARSEABLE_MIME_TYPE) {
                CompositeMetadataCodec.encodeAndAddMetadata(compositeByteBuf, PooledByteBufAllocator.DEFAULT, wellKnownMimeType, entry.getValue());
            } else {
                CompositeMetadataCodec.encodeAndAddMetadata(compositeByteBuf, PooledByteBufAllocator.DEFAULT, entry.getKey(), entry.getValue());
            }
        }
        return compositeByteBuf;
    }


    @Override
    public void load(ByteBuf byteBuf) {
        CompositeMetadata compositeMetadata = new CompositeMetadata(byteBuf, false);
        for (CompositeMetadata.Entry entry : compositeMetadata) {
            String mimeType = entry.getMimeType();
            ByteBuf bytes = entry.getContent();
            metadataBytesStore.put(mimeType, bytes);

            RSocketMimeType rsocketMimeType = RSocketMimeType.getByType(mimeType);
            if (Objects.nonNull(rsocketMimeType)) {
                try {
                    bytes.markReaderIndex();
                    MetadataAware metadataAware = MetadataAwares.instance(rsocketMimeType, bytes);
                    bytes.resetReaderIndex();
                    metadataStore.put(rsocketMimeType, metadataAware);
                } catch (Exception e) {
                    ExceptionUtils.throwExt(e);
                }
            }
        }
    }

    /**
     * 添加metadata
     */
    public void addMetadata(MetadataAware metadata) {
        metadataBytesStore.put(metadata.getMimeType(), metadata.getContent());
        metadataStore.put(metadata.mimeType(), metadata);
    }

    /**
     * @return meta data bytes
     */
    public ByteBuf getMetadataBytes(RSocketMimeType mimeType) {
        return metadataBytesStore.get(mimeType.getType());
    }

    /**
     * @return 是否包含该meta data
     */
    public boolean contains(RSocketMimeType mimeType) {
        return metadataBytesStore.containsKey(mimeType.getType());
    }

    /**
     * @return meta data实现类
     */
    @SuppressWarnings("unchecked")
    public <T extends MetadataAware> T getMetadata(RSocketMimeType mimeType) {
        return (T) metadataStore.get(mimeType);
    }
}
