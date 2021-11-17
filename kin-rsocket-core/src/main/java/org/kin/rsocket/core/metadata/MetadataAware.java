package org.kin.rsocket.core.metadata;

import io.netty.buffer.ByteBuf;
import io.rsocket.metadata.CompositeMetadata;
import org.kin.rsocket.core.RSocketMimeType;

import javax.annotation.Nonnull;

/**
 * @author huangjianqin
 * @date 2021/3/24
 */
public interface MetadataAware extends CompositeMetadata.Entry {
    /**
     * @return RSocket MIME type enum
     */
    RSocketMimeType mimeType();

    /**
     * @return RSocket MIME type
     */
    @Override
    default String getMimeType() {
        return mimeType().getType();
    }

    /**
     * 获取metadata bytes
     *
     * @return metadata bytes
     */
    @Nonnull
    @Override
    ByteBuf getContent();

    /**
     * load metadata from byte buffer
     *
     * @param byteBuf byte buf
     * @throws Exception exception
     */
    void load(ByteBuf byteBuf);

}