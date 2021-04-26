package org.kin.rsocket.core.metadata;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.rsocket.metadata.WellKnownMimeType;
import org.kin.rsocket.core.RSocketMimeType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static io.rsocket.metadata.WellKnownMimeType.UNPARSEABLE_MIME_TYPE;

/**
 * 允许传输broker未知(未实现)mimetype
 *
 * @author huangjianqin
 * @date 2021/3/25
 */
public final class MessageAcceptMimeTypesMetadata implements MetadataAware {
    /** accept的mime type id, 也可能是{@link WellKnownMimeType#UNPARSEABLE_MIME_TYPE}未知mime type str */
    private final List<Object> mimeTypes = new ArrayList<>();
    /** bytebuf size */
    private int byteBufLength = 0;

    public static MessageAcceptMimeTypesMetadata of(String... acceptedMimeTypes) {
        MessageAcceptMimeTypesMetadata metadata = new MessageAcceptMimeTypesMetadata();
        for (String acceptedMimeType : acceptedMimeTypes) {
            WellKnownMimeType wellKnownMimeType = WellKnownMimeType.fromString(acceptedMimeType);
            if (wellKnownMimeType == UNPARSEABLE_MIME_TYPE) {
                metadata.mimeTypes.add(acceptedMimeType);
                metadata.byteBufLength += (acceptedMimeTypes.length + 1);
            } else {
                metadata.mimeTypes.add(wellKnownMimeType.getIdentifier());
                metadata.byteBufLength += 1;
            }
        }

        return metadata;
    }

    public static MessageAcceptMimeTypesMetadata of(WellKnownMimeType... wellKnownMimeTypes) {
        MessageAcceptMimeTypesMetadata metadata = new MessageAcceptMimeTypesMetadata();
        for (WellKnownMimeType wellKnownMimeType : wellKnownMimeTypes) {
            metadata.mimeTypes.add(wellKnownMimeType.getIdentifier());
        }
        metadata.byteBufLength = wellKnownMimeTypes.length;
        return metadata;
    }

    public static MessageAcceptMimeTypesMetadata of(RSocketMimeType... rsocketMimeTypes) {
        MessageAcceptMimeTypesMetadata metadata = new MessageAcceptMimeTypesMetadata();
        for (RSocketMimeType rsocketMimeType : rsocketMimeTypes) {
            metadata.mimeTypes.add(rsocketMimeType.getId());
        }
        metadata.byteBufLength = rsocketMimeTypes.length;
        return metadata;
    }

    public static MessageAcceptMimeTypesMetadata of(ByteBuf content) {
        MessageAcceptMimeTypesMetadata metadata = new MessageAcceptMimeTypesMetadata();
        metadata.load(content);
        return metadata;
    }

    private MessageAcceptMimeTypesMetadata() {
    }

    @Override
    public RSocketMimeType mimeType() {
        return RSocketMimeType.MessageAcceptMimeTypes;
    }

    /**
     * 获取第一个accept mime type
     *
     * @return 第一个accept mime type
     */
    public RSocketMimeType getFirstAcceptType() {
        Object mimeType = mimeTypes.get(0);
        if (mimeType instanceof Byte) {
            return RSocketMimeType.getById((Byte) mimeType);
        } else if (mimeType instanceof String) {
            return RSocketMimeType.getByType((String) mimeType);
        }
        return null;
    }

    @Override
    public ByteBuf getContent() {
        ByteBuf buffer = PooledByteBufAllocator.DEFAULT.buffer(this.byteBufLength);
        for (Object mimeType : mimeTypes) {
            if (mimeType instanceof Byte) {
                buffer.writeByte((byte) ((byte) mimeType | 0x80));
            } else if (mimeType instanceof String) {
                byte[] bytes = ((String) mimeType).getBytes(StandardCharsets.US_ASCII);
                buffer.writeByte(bytes.length);
                buffer.writeBytes(bytes);
            }
        }
        return buffer;
    }

    @Override
    public void load(ByteBuf byteBuf) {
        this.byteBufLength = byteBuf.readableBytes();
        while (byteBuf.isReadable()) {
            byte firstByte = byteBuf.readByte();
            if (firstByte < 0) {
                byte mimeTypeId = (byte) (firstByte & 0x7F);
                this.mimeTypes.add(WellKnownMimeType.fromIdentifier(mimeTypeId).getString());
            } else {
                this.mimeTypes.add(byteBuf.readCharSequence(firstByte, StandardCharsets.US_ASCII));
            }
        }
    }

    //getter
    public List<Object> getMimeTypes() {
        return mimeTypes;
    }
}

