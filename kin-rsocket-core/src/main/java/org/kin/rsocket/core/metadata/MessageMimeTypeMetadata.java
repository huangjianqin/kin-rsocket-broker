package org.kin.rsocket.core.metadata;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.rsocket.metadata.WellKnownMimeType;
import org.kin.rsocket.core.RSocketMimeType;

import java.nio.charset.StandardCharsets;

/**
 * 允许传输broker未知(未实现)mimetype
 *
 * @author huangjianqin
 * @date 2021/3/25
 */
public final class MessageMimeTypeMetadata implements MetadataAware {
    /** rsocket mime type id */
    private byte mimeTypeId;
    /** rsocket mime type str */
    private String mimeType;

    public static MessageMimeTypeMetadata from(String mimeType) {
        MessageMimeTypeMetadata metadata = new MessageMimeTypeMetadata();
        metadata.mimeType = mimeType;
        try {
            WellKnownMimeType wellKnownMimeType = WellKnownMimeType.fromString(mimeType);
            metadata.mimeTypeId = wellKnownMimeType.getIdentifier();
        } catch (Exception ignore) {
            //do nothing
        }

        return metadata;
    }

    public static MessageMimeTypeMetadata from(WellKnownMimeType knownMimeType) {
        MessageMimeTypeMetadata metadata = new MessageMimeTypeMetadata();
        metadata.mimeTypeId = knownMimeType.getIdentifier();
        metadata.mimeType = knownMimeType.getString();

        return metadata;
    }

    public static MessageMimeTypeMetadata from(RSocketMimeType rsocketMimeType) {
        MessageMimeTypeMetadata metadata = new MessageMimeTypeMetadata();
        metadata.mimeTypeId = rsocketMimeType.getId();
        metadata.mimeType = rsocketMimeType.getType();

        return metadata;
    }

    public static MessageMimeTypeMetadata from(ByteBuf content) {
        MessageMimeTypeMetadata metadata = new MessageMimeTypeMetadata();
        metadata.load(content);
        return metadata;
    }

    private MessageMimeTypeMetadata() {
    }

    @Override
    public RSocketMimeType mimeType() {
        return RSocketMimeType.MESSAGE_MIME_TYPE;
    }

    @Override
    public ByteBuf getContent() {
        if (mimeTypeId > 0) {
            //已知的mimeType第8位都是0, 即小于0x80
            byte[] bytes = {(byte) (mimeTypeId | 0x80)};
            return PooledByteBufAllocator.DEFAULT.buffer(bytes.length).writeBytes(bytes);
        } else {
            //unknown mimeType
            byte[] bytes = mimeType.getBytes(StandardCharsets.US_ASCII);
            ByteBuf buffer = PooledByteBufAllocator.DEFAULT.buffer(bytes.length + 1);
            buffer.writeByte(bytes.length);
            buffer.writeBytes(bytes);
            return buffer;
        }
    }

    @Override
    public void load(ByteBuf byteBuf) {
        byte firstByte = byteBuf.readByte();
        if (firstByte < 0) {
            this.mimeTypeId = (byte) (firstByte & 0x7F);
            this.mimeType = WellKnownMimeType.fromIdentifier(mimeTypeId).getString();
        } else {
            //unknown mimeType
            byteBuf.readCharSequence(firstByte, StandardCharsets.US_ASCII);
        }
    }

    //getter
    public byte getMimeTypeId() {
        return mimeTypeId;
    }

    /**
     * @return message 的mime type
     */
    public RSocketMimeType getMessageMimeType() {
        return RSocketMimeType.getByType(this.mimeType);
    }
}