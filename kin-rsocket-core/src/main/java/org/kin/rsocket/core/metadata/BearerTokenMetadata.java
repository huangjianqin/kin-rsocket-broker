package org.kin.rsocket.core.metadata;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.rsocket.metadata.AuthMetadataCodec;
import io.rsocket.metadata.WellKnownAuthType;
import org.kin.rsocket.core.RSocketMimeType;

/**
 * bearer token metadata, please refer https://github.com/rsocket/rsocket/blob/master/Extensions/Security/Authentication.md
 *
 * @author huangjianqin
 * @date 2021/3/24
 */
public final class BearerTokenMetadata implements MetadataAware {
    /** Bearer Token */
    private char[] bearerToken;

    public static BearerTokenMetadata jwt(char[] credentials) {
        BearerTokenMetadata metadata = new BearerTokenMetadata();
        metadata.bearerToken = credentials;
        return metadata;
    }

    public static BearerTokenMetadata of(ByteBuf content) {
        BearerTokenMetadata metadata = new BearerTokenMetadata();
        metadata.load(content);
        return metadata;
    }

    private BearerTokenMetadata() {
    }

    @Override
    public RSocketMimeType mimeType() {
        return RSocketMimeType.BearerToken;
    }

    @Override
    public ByteBuf getContent() {
        return AuthMetadataCodec.encodeBearerMetadata(PooledByteBufAllocator.DEFAULT, bearerToken);
    }

    @Override
    public void load(ByteBuf byteBuf) {
        WellKnownAuthType wellKnownAuthType = AuthMetadataCodec.readWellKnownAuthType(byteBuf);
        if (wellKnownAuthType == WellKnownAuthType.BEARER) {
            this.bearerToken = AuthMetadataCodec.readBearerTokenAsCharArray(byteBuf);
        }
    }

    /**
     * format routing as "r:%s,s:%s" style
     *
     * @return data format
     */
    private String formatData() {
        return new String(bearerToken);
    }

    @Override
    public String toString() {
        return formatData();
    }

    //setter && getter
    public char[] getBearerToken() {
        return bearerToken;
    }
}
