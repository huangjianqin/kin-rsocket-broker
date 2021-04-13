package org.kin.rsocket.broker.cluster;

import org.kin.rsocket.core.event.CloudEventData;
import reactor.core.publisher.Sinks;

/**
 * @author huangjianqin
 * @date 2021/3/29
 */
public abstract class AbstractBrokerManager implements BrokerManager {
    private final Sinks.Many<CloudEventData<?>> cloudEventSink;

    public AbstractBrokerManager(Sinks.Many<CloudEventData<?>> cloudEventSink) {
        this.cloudEventSink = cloudEventSink;
    }

    /**
     * 处理通过gossip广播的cloud event
     */
    protected void handleCloudEvent(CloudEventData<?> cloudEvent) {
        cloudEventSink.tryEmitNext(cloudEvent);
    }
}
