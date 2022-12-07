package org.kin.rsocket.core.event;

import io.cloudevents.CloudEvent;

import java.util.UUID;

/**
 * @author huangjianqin
 * @date 2021/4/20
 */
public class AppStatusEventConsumerTest {
    public static void main(String[] args) {
        AppStatusEventConsumer consumer = new AppStatusEventConsumer();

        AppStatusEvent event = AppStatusEvent.connected(UUID.randomUUID().toString());
        consumer.consume(event.toCloudEvent());
    }

    private static class AppStatusEventConsumer extends AbstractCloudEventConsumer<AppStatusEvent> {
        @Override
        protected void consume(CloudEvent cloudEvent, AppStatusEvent event) {
            System.out.println(cloudEvent);
        }
    }
}
