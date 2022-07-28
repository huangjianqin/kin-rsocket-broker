package org.kin.rsocket.core.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.buffer.Unpooled;
import io.rsocket.Payload;
import io.rsocket.util.ByteBufPayload;
import org.kin.framework.utils.ExceptionUtils;
import org.kin.framework.utils.JSON;
import org.kin.rsocket.core.RSocketMimeType;
import org.kin.rsocket.core.metadata.RSocketCompositeMetadata;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * @author huangjianqin
 * @date 2021/3/23
 */
@SuppressWarnings("unchecked")
public interface CloudEventSupport extends Serializable {
    /**
     * 解析cloud event数据
     */
    static <T extends CloudEventSupport> T unwrapData(CloudEventData<?> cloudEvent, Class<T> targetClass) {
        return cloudEvent.getData().map(data -> {
            try {
                if (data instanceof ObjectNode || data instanceof Map) {
                    return JSON.convert(data, targetClass);
                } else if (data.getClass().isAssignableFrom(targetClass)) {
                    return (T) data;
                } else if (data instanceof String) {
                    return JSON.read((String) data, targetClass);
                }
            } catch (Exception ignore) {
                //do nothing
            }
            return null;
        }).orElse(null);
    }

    /**
     * cloud event转换成payload
     */
    @Nonnull
    static Payload cloudEvent2Payload(CloudEventData<?> cloudEvent) {
        try {
            return ByteBufPayload.create(Unpooled.EMPTY_BUFFER, Unpooled.wrappedBuffer(org.kin.rsocket.core.utils.JSON.serialize(cloudEvent)));
        } catch (Exception e) {
            ExceptionUtils.throwExt(e);
        }
        return null;
    }

    /**
     * cloud event转换成payload
     */
    @Nonnull
    static Payload cloudEvent2Payload(String cloudEventJson) {
        try {
            return ByteBufPayload.create(Unpooled.EMPTY_BUFFER, Unpooled.wrappedBuffer(cloudEventJson.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            ExceptionUtils.throwExt(e);
        }
        return null;
    }

    /**
     * 从payload metadata中获取元数据
     */
    static CloudEventData<JsonNode> extractCloudEventsFromMetadata(Payload payload) {
        String jsonText = null;
        byte firstByte = payload.metadata().getByte(0);
        // json text: well known type > 127, and normal mime type's length < 127
        if (firstByte == '{') {
            jsonText = payload.getMetadataUtf8();
        } else {
            //composite metadata
            RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.from(payload.metadata());
            if (compositeMetadata.contains(RSocketMimeType.CLOUD_EVENTS_JSON)) {
                jsonText = compositeMetadata.getMetadataBytes(RSocketMimeType.CLOUD_EVENTS_JSON).toString(StandardCharsets.UTF_8);
            }
        }
        if (jsonText != null) {
            return org.kin.rsocket.core.utils.JSON.decodeValue(jsonText);
        }
        return null;
    }

    /**
     * 转换成cloud event data
     */
    default <T extends CloudEventSupport> CloudEventData<T> toCloudEvent() {
        return CloudEventBuilder.builder((T) this).build();
    }
}

