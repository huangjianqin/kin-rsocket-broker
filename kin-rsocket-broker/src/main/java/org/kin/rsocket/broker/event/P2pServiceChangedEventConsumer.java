package org.kin.rsocket.broker.event;

import io.cloudevents.CloudEvent;
import org.kin.rsocket.broker.RSocketServiceRegistry;
import org.kin.rsocket.core.event.AbstractCloudEventConsumer;
import org.kin.rsocket.core.event.P2pServiceChangedEvent;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author huangjianqin
 * @date 2021/8/17
 */
public final class P2pServiceChangedEventConsumer extends AbstractCloudEventConsumer<P2pServiceChangedEvent> {
    @Autowired
    private RSocketServiceRegistry serviceRegistry;

    @Override
    protected void consume(CloudEvent cloudEvent, P2pServiceChangedEvent event) {
        serviceRegistry.updateP2pServiceConsumers(event.getAppId(), event.getP2pServiceIds());
    }
}
