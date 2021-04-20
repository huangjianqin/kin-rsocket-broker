package org.kin.rsocket.broker.event;

import org.kin.rsocket.broker.ServiceManager;
import org.kin.rsocket.broker.ServiceResponder;
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
            ServiceResponder responder = serviceManager.getByUUID(event.getAppId());
            if (responder != null) {
                AppMetadata appMetadata = responder.getAppMetadata();
                appMetadata.setWebPort(event.getWebPort());
                appMetadata.setManagementPort(event.getManagementPort());
                appMetadata.setRsocketPorts(event.getRsocketPorts());
            }
        }
        return Mono.empty();
    }
}
