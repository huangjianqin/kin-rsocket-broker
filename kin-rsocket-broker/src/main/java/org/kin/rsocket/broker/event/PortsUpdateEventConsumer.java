package org.kin.rsocket.broker.event;

import org.kin.rsocket.broker.BrokerResponder;
import org.kin.rsocket.broker.ServiceManager;
import org.kin.rsocket.core.event.AbstractCloudEventConsumer;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.PortsUpdateEvent;
import org.kin.rsocket.core.metadata.AppMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;

/**
 * @author huangjianqin
 * @date 2021/3/30
 */
public final class PortsUpdateEventConsumer extends AbstractCloudEventConsumer<PortsUpdateEvent> {
    @Autowired
    private ServiceManager serviceManager;

    @Override
    public Mono<Void> consume(CloudEventData<?> cloudEventData, PortsUpdateEvent event) {
        if (event != null) {
            BrokerResponder responder = serviceManager.getByUUID(event.getAppId());
            if (responder != null) {
                AppMetadata appMetadata = responder.getAppMetadata();
                appMetadata.updateWebPort(event.getWebPort());
                appMetadata.updateManagementPort(event.getManagementPort());
                appMetadata.updateRsocketPorts(event.getRsocketPorts());
            }
        }
        return Mono.empty();
    }
}
