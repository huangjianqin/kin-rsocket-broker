package org.kin.rsocket.core.event;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * @author huangjianqin
 * @date 2021/4/20
 */
public class AppStatusEventConsumerTest {
    public static void main(String[] args) {
        AppStatusEventConsumer consumer = new AppStatusEventConsumer();

        AppStatusEvent event = AppStatusEvent.connected(UUID.randomUUID().toString());
        consumer.consume(CloudEventBuilder.builder(event).build());
    }

    private static class AppStatusEventConsumer extends AbstractCloudEventConsumer<AppStatusEvent> {
        @Override
        protected Mono<Void> consume(CloudEventData<?> cloudEventData, AppStatusEvent cloudEvent) {
            System.out.println(cloudEvent);
            return Mono.empty();
        }
    }
}
