package org.kin.rsocket.broker.cluster;

import org.kin.rsocket.core.RSocketAppContext;
import org.kin.rsocket.core.event.CloudEventData;

/**
 * @author huangjianqin
 * @date 2021/3/29
 */
public abstract class AbstractBrokerManager implements BrokerManager {
    /**
     * 处理通过gossip广播的cloud event
     */
    protected void handleCloudEvent(CloudEventData<?> cloudEvent) {
        RSocketAppContext.CLOUD_EVENT_SINK.tryEmitNext(cloudEvent);
    }
}
