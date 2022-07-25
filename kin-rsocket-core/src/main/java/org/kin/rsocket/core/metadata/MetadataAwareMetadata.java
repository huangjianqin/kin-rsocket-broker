package org.kin.rsocket.core.metadata;

import io.netty.buffer.ByteBuf;

import java.util.function.Function;

/**
 * @author huangjianqin
 * @date 2022/7/25
 */
public final class MetadataAwareMetadata {
    /** {@link MetadataAware}实现类 */
    private final Class<? extends MetadataAware> metadataAwareClass;
    /** MetadataAware实现类中of(ByteBuf)静态方法, 作为构造MetadataAware实例的入口 */
    private final Function<ByteBuf, ? extends MetadataAware> parseBytesFunc;

    public MetadataAwareMetadata(Class<? extends MetadataAware> metadataAwareClass, Function<ByteBuf, ? extends MetadataAware> parseBytesFunc) {
        this.metadataAwareClass = metadataAwareClass;
        this.parseBytesFunc = parseBytesFunc;
    }

    //getter
    public Class<? extends MetadataAware> getMetadataAwareClass() {
        return metadataAwareClass;
    }

    public Function<ByteBuf, ? extends MetadataAware> getParseBytesFunc() {
        return parseBytesFunc;
    }
}
