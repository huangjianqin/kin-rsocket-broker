package org.kin.rsocket.service.boot.event;

import io.cloudevents.CloudEvent;
import org.kin.rsocket.core.event.CloudEventConsumer;
import org.kin.rsocket.core.event.CloudEventSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
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
    public boolean shouldAccept(CloudEvent cloudEvent) {
        return true;
    }

    @Override
    public Mono<Void> consume(CloudEvent cloudEvent) {
        Object unwrappedObj = CloudEventSupport.unwrapData(cloudEvent);
        if (!(unwrappedObj instanceof ApplicationEvent)) {
            return Mono.empty();
        }

        ApplicationEvent applicationEvent = (ApplicationEvent) unwrappedObj;
        return Mono.fromRunnable(() -> eventPublisher.publishEvent(applicationEvent));
    }
}

