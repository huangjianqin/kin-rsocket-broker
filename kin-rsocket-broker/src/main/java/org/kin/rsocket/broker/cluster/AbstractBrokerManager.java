package org.kin.rsocket.broker.cluster;

import org.kin.rsocket.core.event.CloudEventData;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.extra.processor.TopicProcessor;

/**
 * @author huangjianqin
 * @date 2021/3/29
 */
public abstract class AbstractBrokerManager implements BrokerManager {
    @Autowired
    private TopicProcessor<CloudEventData<?>> cloudEventProcessor;

    /**
     * 处理通过gossip广播的cloud event
     */
    protected void handleCloudEvent(CloudEventData<?> cloudEvent) {
        cloudEventProcessor.onNext(cloudEvent);
    }
}
