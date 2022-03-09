package org.kin.rsocket.broker.event;

import org.kin.rsocket.broker.RSocketEndpoint;
import org.kin.rsocket.broker.RSocketServiceManager;
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
    private RSocketServiceManager serviceManager;

    @Override
    public Mono<Void> consume(CloudEventData<?> cloudEventData, PortsUpdateEvent event) {
        if (event != null) {
            RSocketEndpoint rsocketEndpoint = serviceManager.getByUUID(event.getAppId());
            if (rsocketEndpoint != null) {
                AppMetadata appMetadata = rsocketEndpoint.getAppMetadata();
                appMetadata.updateWebPort(event.getWebPort());
                appMetadata.updateManagementPort(event.getManagementPort());
                appMetadata.updateRSocketPorts(event.getRsocketPorts());
            }
        }
        return Mono.empty();
    }
}
