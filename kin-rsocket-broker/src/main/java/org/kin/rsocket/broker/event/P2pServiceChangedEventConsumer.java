package org.kin.rsocket.broker.event;

import org.kin.rsocket.broker.BrokerResponder;
import org.kin.rsocket.broker.RSocketServiceManager;
import org.kin.rsocket.core.event.AbstractCloudEventConsumer;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.P2pServiceChangedEvent;
import org.kin.rsocket.core.metadata.AppMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;

/**
 * @author huangjianqin
 * @date 2021/8/17
 */
public final class P2pServiceChangedEventConsumer extends AbstractCloudEventConsumer<P2pServiceChangedEvent> {
    @Autowired
    private RSocketServiceManager serviceManager;

    @Override
    protected Mono<Void> consume(CloudEventData<?> cloudEventData, P2pServiceChangedEvent cloudEvent) {
        if (cloudEvent != null) {
            BrokerResponder responder = serviceManager.getByUUID(cloudEvent.getAppId());
            if (responder != null) {
                AppMetadata appMetadata = responder.getAppMetadata();
                appMetadata.updateP2pServices(cloudEvent.getP2pServices());
            }
        }
        return Mono.empty();
    }
}
