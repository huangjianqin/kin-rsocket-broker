package org.kin.rsocket.service.boot.event;

import org.kin.rsocket.core.event.CloudEventConsumer;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.CloudEventSupport;
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
    public boolean shouldAccept(CloudEventData<?> cloudEventData) {
        return true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<Void> consume(CloudEventData<?> cloudEventData) {
        String className = cloudEventData.getAttributes().getType();
        Class<? extends CloudEventSupport> cloudEventClass;
        try {
            cloudEventClass = (Class<? extends CloudEventSupport>) Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(String.format("unknown cloud event class '%s'", className));
        }
        return Mono.fromRunnable(() -> eventPublisher.publishEvent(CloudEventSupport.unwrapData(cloudEventData, cloudEventClass)));
    }
}

