package org.kin.rsocket.core.event;

import reactor.core.publisher.Mono;

/**
 * consume cloud event
 *
 * @author huangjianqin
 * @date 2021/3/24
 */
public interface CloudEventConsumer {
    /** 是否接受该cloud event */
    boolean shouldAccept(CloudEventData<?> cloudEvent);

    /** consume cloud event具体逻辑 */
    Mono<Void> consume(CloudEventData<?> cloudEvent);
}
