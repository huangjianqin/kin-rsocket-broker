package org.kin.rsocket.core.metadata;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.rsocket.metadata.WellKnownMimeType;
import org.kin.rsocket.core.RSocketMimeType;

import java.nio.charset.StandardCharsets;

/**
 * @author huangjianqin
 * @date 2021/3/25
 */
public class MessageMimeTypeMetadata implements MetadataAware {
    /** rsocket mime type id todo 是否可优化成仅仅传输id */
    private byte mimeTypeId;
    /** rsocket mime type str */
    private String mimeType;

    public static MessageMimeTypeMetadata of(String mimeType) {
        MessageMimeTypeMetadata metadata = new MessageMimeTypeMetadata();
        metadata.mimeType = mimeType;
        try {
            WellKnownMimeType wellKnownMimeType = WellKnownMimeType.fromString(mimeType);
            metadata.mimeTypeId = wellKnownMimeType.getIdentifier();
        } catch (Exception ignore) {

        }

        return metadata;
    }

    public static MessageMimeTypeMetadata of(WellKnownMimeType knownMimeType) {
        MessageMimeTypeMetadata metadata = new MessageMimeTypeMetadata();
        metadata.mimeTypeId = knownMimeType.getIdentifier();
        metadata.mimeType = knownMimeType.getString();

        return metadata;
    }

    public static MessageMimeTypeMetadata of(RSocketMimeType rsocketMimeType) {
        MessageMimeTypeMetadata metadata = new MessageMimeTypeMetadata();
        metadata.mimeTypeId = rsocketMimeType.getId();
        metadata.mimeType = rsocketMimeType.getType();

        return metadata;
    }

    public static MessageMimeTypeMetadata of(ByteBuf content) {
        MessageMimeTypeMetadata metadata = new MessageMimeTypeMetadata();
        metadata.load(content);
        return metadata;
    }

    /**
     * 构建编码元数据对应的bytebuf
     */
    public static ByteBuf toByteBuf(MessageMimeTypeMetadata metadata) {
        //todo
        ByteBuf buf = Unpooled.buffer(5, 5);
        buf.writeByte((byte) (WellKnownMimeType.MESSAGE_RSOCKET_MIMETYPE.getIdentifier() | 0x80));
        buf.writeByte(0);
        buf.writeByte(0);
        buf.writeByte(1);
        buf.writeByte(metadata.getMessageMimeType().getId() | 0x80);
        return buf;
    }

    @Override
    public RSocketMimeType mimeType() {
        return RSocketMimeType.MessageMimeType;
    }

    @Override
    public ByteBuf getContent() {
        if (mimeTypeId > 0) {
            return Unpooled.wrappedBuffer(new byte[]{(byte) (mimeTypeId | 0x80)});
        } else {
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
            byteBuf.readCharSequence(firstByte, StandardCharsets.US_ASCII);
        }
    }

    /**
     * @return message 的mime type
     */
    public RSocketMimeType getMessageMimeType() {
        return RSocketMimeType.getByType(this.mimeType);
    }
}