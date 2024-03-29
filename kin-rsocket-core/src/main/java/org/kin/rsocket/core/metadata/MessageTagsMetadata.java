package org.kin.rsocket.core.metadata;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.rsocket.metadata.TaggingMetadata;
import io.rsocket.metadata.TaggingMetadataCodec;
import org.kin.rsocket.core.RSocketMimeType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author huangjianqin
 * @date 2021/3/25
 */
public final class MessageTagsMetadata implements MetadataAware {
    /** credentials */
    private Map<String, String> tags;

    public static MessageTagsMetadata from(Map<String, String> tags) {
        MessageTagsMetadata metadata = new MessageTagsMetadata();
        metadata.tags = tags;
        return metadata;
    }

    public static MessageTagsMetadata from(ByteBuf content) {
        MessageTagsMetadata metadata = new MessageTagsMetadata();
        metadata.load(content);
        return metadata;
    }

    private MessageTagsMetadata() {
    }

    @Override
    public RSocketMimeType mimeType() {
        return RSocketMimeType.MESSAGE_TAGS;
    }

    @Override
    public ByteBuf getContent() {
        List<String> temp = new ArrayList<>();
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            temp.add(entry.getKey() + "=" + entry.getValue());
        }
        return TaggingMetadataCodec.createTaggingContent(ByteBufAllocator.DEFAULT, temp);
    }

    @Override
    public void load(ByteBuf byteBuf) {
        TaggingMetadata taggingMetadata = new TaggingMetadata(RSocketMimeType.MESSAGE_TAGS.getType(), byteBuf);
        taggingMetadata.forEach(pair -> {
            int start = pair.indexOf("=");
            String name = pair.substring(0, start);
            String value = pair.substring(start + 1);
            tags.put(name, value);
        });
    }

    /**
     * format routing as "k1=v1\nk2=v2\n" style
     *
     * @return data format
     */
    private String formatData() {
        return this.tags.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));
    }

    @Override
    public String toString() {
        return formatData();
    }

    //setter && getter
    public Map<String, String> getTags() {
        return tags;
    }
}
