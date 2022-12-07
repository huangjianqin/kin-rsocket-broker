package org.kin.rsocket.core.event;

import io.cloudevents.CloudEvent;
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
     *
     * @param cloudEvent cloud event
     * @return complete signal
     */
    Mono<Void> fireCloudEvent(CloudEvent cloudEvent);

    /**
     * 广播cloud event bytes
     *
     * @param cloudEventBytes cloud event bytes
     * @return complete signal
     */
    Mono<Void> fireCloudEvent(byte[] cloudEventBytes);
}
