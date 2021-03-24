package org.kin.rsocket.core.event;

import com.fasterxml.jackson.databind.JsonNode;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import reactor.core.publisher.Mono;

import java.net.URI;

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
     * @param replyTo
     * @param eventReply
     * @return
     */
    default Payload toCloudEventReplyPayload(URI replyTo, CloudEventReply eventReply) {
        //TODO
//        String path = replyTo.getPath();
//        String serviceName = path.substring(path.lastIndexOf("/") + 1);
//        String method = replyTo.getFragment();
//        RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.from(new GSVRoutingMetadata("", serviceName, method, ""), new MessageMimeTypeMetadata(WellKnownMimeType.APPLICATION_JSON));
//        return ByteBufPayload.create(JsonUtils.toJsonByteBuf(eventReply), compositeMetadata.getContent());
        return null;
    }

    /**
     * @param cloudEvent
     * @return
     */
    default Payload cloudEvent2Payload(CloudEventData<?> cloudEvent) {
        //todo
//        try {
//            return ByteBufPayload.create(Unpooled.EMPTY_BUFFER, Unpooled.wrappedBuffer(Json.serialize(cloudEvent)));
//        } catch (Exception e) {
//            throw new EncodingException(RsocketErrorCode.message("RST-700500", "CloudEventImpl", "ByteBuf"), e);
//        }
        return null;
    }

    /**
     * @param payload
     * @return
     */
    default CloudEventData<JsonNode> extractCloudEventsFromMetadata(Payload payload) {
        //todo
//        String jsonText = null;
//        byte firstByte = payload.metadata().getByte(0);
//        // json text: well known type > 127, and normal mime type's length < 127
//        if (firstByte == '{') {
//            jsonText = payload.getMetadataUtf8();
//        } else {  //composite metadata
//            RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.from(payload.metadata());
//            if (compositeMetadata.contains(RSocketMimeType.CloudEventsJson)) {
//                jsonText = compositeMetadata.getMetadata(RSocketMimeType.CloudEventsJson).toString(StandardCharsets.UTF_8);
//            }
//        }
//        if (jsonText != null) {
//            return Json.decodeValue(jsonText);
//        }
//        return null;
        return null;
    }

}
