package org.kin.rsocket.core.event;

import com.fasterxml.jackson.databind.JsonNode;
import io.netty.buffer.Unpooled;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.metadata.WellKnownMimeType;
import io.rsocket.util.ByteBufPayload;
import org.kin.framework.utils.ExceptionUtils;
import org.kin.rsocket.core.metadata.GSVRoutingMetadata;
import org.kin.rsocket.core.metadata.MessageMimeTypeMetadata;
import org.kin.rsocket.core.metadata.RSocketCompositeMetadata;
import org.kin.rsocket.core.metadata.RSocketMimeType;
import org.kin.rsocket.core.utils.JSON;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * 支持request(广播)/response(reply) cloud event的rsocket
 *
 * @author huangjianqin
 * @date 2021/3/23
 */
public interface CloudEventRSocket extends RSocket {
    /**
     * 广播cloud event
     */
    Mono<Void> fireCloudEvent(CloudEventData<?> cloudEvent);

    /**
     * 给指定rsocket responder返回CloudEventReply
     *
     * @param replayTo 指定rsocket responder
     */
    Mono<Void> fireCloudEventReply(URI replayTo, CloudEventReply eventReply);

    /**
     * cloud event reply转换成payload
     */
    default Payload cloudEventReply2Payload(URI replyTo, CloudEventReply eventReply) {
        String path = replyTo.getPath();
        String serviceName = path.substring(path.lastIndexOf("/") + 1);
        String method = replyTo.getFragment();
        RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(
                GSVRoutingMetadata.of("", serviceName, method, ""),
                MessageMimeTypeMetadata.of(WellKnownMimeType.APPLICATION_JSON));
        return ByteBufPayload.create(JSON.writeByteBuf(eventReply), compositeMetadata.getContent());
    }

    /**
     * cloud event转换成payload
     */
    default Payload cloudEvent2Payload(CloudEventData<?> cloudEvent) {
        try {
            return ByteBufPayload.create(Unpooled.EMPTY_BUFFER, Unpooled.wrappedBuffer(JSON.serialize(cloudEvent)));
        } catch (Exception e) {
            ExceptionUtils.throwExt(e);
        }
        return null;
    }

    /**
     * 从payload metadata中获取元数据
     */
    default CloudEventData<JsonNode> extractCloudEventsFromMetadata(Payload payload) {
        String jsonText = null;
        byte firstByte = payload.metadata().getByte(0);
        // json text: well known type > 127, and normal mime type's length < 127
        if (firstByte == '{') {
            jsonText = payload.getMetadataUtf8();
        } else {
            //composite metadata
            RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.from(payload.metadata());
            if (compositeMetadata.contains(RSocketMimeType.CloudEventsJson)) {
                jsonText = compositeMetadata.getMetadataBytes(RSocketMimeType.CloudEventsJson).toString(StandardCharsets.UTF_8);
            }
        }
        if (jsonText != null) {
            return JSON.decodeValue(jsonText);
        }
        return null;
    }

}
