package org.kin.rsocket.broker.cluster;

import io.cloudevents.CloudEvent;
import org.kin.rsocket.core.event.CloudEventBus;

/**
 * @author huangjianqin
 * @date 2021/3/29
 */
public abstract class AbstractRSocketBrokerManager implements RSocketBrokerManager {
    /**
     * 处理通过gossip广播的cloud event
     */
    protected void handleCloudEvent(CloudEvent cloudEvent) {
        CloudEventBus.INSTANCE.postCloudEvent(cloudEvent);
    }
}
