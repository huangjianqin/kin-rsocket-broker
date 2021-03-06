package org.kin.rsocket.core.event;

import io.rsocket.RSocket;
import reactor.core.publisher.Mono;

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
}
