package org.kin.rsocket.springcloud.service.event;

import org.kin.rsocket.core.event.CloudEventConsumer;
import org.kin.rsocket.core.event.CloudEventData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Mono;

/**
 * 将cloud event通过{@link org.springframework.context.ApplicationListener}广播出去
 *
 * @author huangjianqin
 * @date 2021/3/28
 */
public final class CloudEvent2ApplicationEventConsumer implements CloudEventConsumer {
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Override
    public boolean shouldAccept(CloudEventData<?> cloudEvent) {
        return true;
    }

    @Override
    public Mono<Void> consume(CloudEventData<?> cloudEvent) {
        return Mono.fromRunnable(() -> {
            eventPublisher.publishEvent(cloudEvent);
        });
    }
}

