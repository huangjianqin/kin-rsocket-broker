package org.kin.rsocket.core.event;

import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.rsocket.Payload;
import io.rsocket.util.ByteBufPayload;
import org.kin.framework.utils.ClassUtils;
import org.kin.framework.utils.ExceptionUtils;
import org.kin.rsocket.core.RSocketMimeType;
import org.kin.rsocket.core.metadata.RSocketCompositeMetadata;
import org.kin.rsocket.core.utils.JSON;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author huangjianqin
 * @date 2021/3/23
 */
public interface CloudEventSupport extends Serializable {
    /**
     * 解析cloud event数据, 并转换成真实cloud event实例
     *
     * @param cloudEvent cloud event
     * @return 真实cloud event实例
     */
    @Nullable
    static <T> T unwrapData(CloudEvent cloudEvent) {
        Class<T> targetClass = ClassUtils.getClass(cloudEvent.getType());
        byte[] bytes = getCloudEventDataBytes(cloudEvent);
        if (Objects.isNull(bytes)) {
            return null;
        }

        return JSON.read(bytes, targetClass);
    }

    /**
     * {@link CloudEvent}转换成{@link ByteBufPayload}
     *
     * @param cloudEvent cloud event
     * @return payload
     */
    @Nonnull
    static Payload cloudEvent2Payload(CloudEvent cloudEvent) {
        try {
            byte[] bytes = JSON.serializeCloudEvent(cloudEvent);
            return ByteBufPayload.create(Unpooled.EMPTY_BUFFER, PooledByteBufAllocator.DEFAULT.buffer(bytes.length).writeBytes(bytes));
        } catch (Exception e) {
            ExceptionUtils.throwExt(e);
        }
        return null;
    }

    /**
     * cloud event json转换成{@link ByteBufPayload}
     *
     * @param cloudEventJson cloud event json
     * @return payload
     */
    @Nonnull
    static Payload cloudEventJson2Payload(String cloudEventJson) {
        return cloudEventBytes2Payload(cloudEventJson.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * cloud event bytes转换成{@link ByteBufPayload}
     *
     * @param cloudEventBytes cloud event bytes
     * @return payload
     */
    @Nonnull
    static Payload cloudEventBytes2Payload(byte[] cloudEventBytes) {
        try {
            return ByteBufPayload.create(Unpooled.EMPTY_BUFFER, PooledByteBufAllocator.DEFAULT.buffer(cloudEventBytes.length).writeBytes(cloudEventBytes));
        } catch (Exception e) {
            ExceptionUtils.throwExt(e);
        }
        return null;
    }

    /**
     * 从payload metadata解析出{@link CloudEvent}实例
     *
     * @param payload payload
     * @return {@link CloudEvent}实例
     */
    static CloudEvent extractCloudEventFromMetadata(Payload payload) {
        String json = null;
        byte firstByte = payload.metadata().getByte(0);
        // json text: well known type > 127, and normal mime type's length < 127
        if (firstByte == '{') {
            json = payload.getMetadataUtf8();
        } else {
            //composite metadata
            RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.from(payload.metadata());
            if (compositeMetadata.contains(RSocketMimeType.CLOUD_EVENTS_JSON)) {
                json = compositeMetadata.getMetadataBytes(RSocketMimeType.CLOUD_EVENTS_JSON).toString(StandardCharsets.UTF_8);
            }
        }
        if (json != null) {
            return JSON.deserializeCloudEvent(json);
        }
        return null;
    }

    /**
     * 转换成{@link CloudEvent}实例
     *
     * @return {@link CloudEvent}实例
     */
    default CloudEvent toCloudEvent() {
        return CloudEventBuilder.builder(this).build();
    }

    /**
     * 解析{@link CloudEvent#getSource()}query参数
     *
     * @param uri cloud event source
     * @return source query param
     */
    static Map<String, String> parseSourceParams(URI uri) {
        Map<String, String> params = new HashMap<>(4);
        QueryStringDecoder decoder = new QueryStringDecoder(uri);
        for (Map.Entry<String, List<String>> entry : decoder.parameters().entrySet()) {
            //entry.getValue()是一个List, 只取第一个元素
            params.put(entry.getKey(), entry.getValue().get(0));
        }
        return params;
    }

    /**
     * 获取cloud event data bytes
     *
     * @param cloudEvent cloud event
     * @return cloud event data bytes
     * @see CloudEvent#getData()
     * @see CloudEventData#toBytes()
     */
    @Nullable
    static byte[] getCloudEventDataBytes(CloudEvent cloudEvent) {
        CloudEventData cloudEventData = cloudEvent.getData();
        if (Objects.nonNull(cloudEventData)) {
            return cloudEventData.toBytes();
        }

        return null;
    }
}

