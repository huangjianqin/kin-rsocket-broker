package org.kin.rsocket.core.event;

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
}
