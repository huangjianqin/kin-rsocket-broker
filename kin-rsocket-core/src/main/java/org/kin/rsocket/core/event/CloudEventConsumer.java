package org.kin.rsocket.core.event;

import io.cloudevents.CloudEvent;
import reactor.core.publisher.Mono;

/**
 * consume cloud event
 *
 * @author huangjianqin
 * @date 2021/3/24
 */
public interface CloudEventConsumer {
    /**
     * 是否允许消费该cloud event
     *
     * @param cloudEvent cloud event
     * @return 允许消费
     */
    boolean shouldAccept(CloudEvent cloudEvent);

    /**
     * consume cloud event具体逻辑
     *
     * @param cloudEvent cloud event
     * @return consume complete signal
     */
    Mono<Void> consume(CloudEvent cloudEvent);
}
