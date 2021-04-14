package org.kin.rsocket.broker.event;

import org.kin.rsocket.broker.ServiceResponder;
import org.kin.rsocket.broker.ServiceResponderManager;
import org.kin.rsocket.core.event.CloudEventConsumer;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.CloudEventSupport;
import org.kin.rsocket.core.event.PortsUpdateEvent;
import org.kin.rsocket.core.metadata.AppMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;

/**
 * @author huangjianqin
 * @date 2021/3/30
 */
public final class PortsUpdateEventConsumer implements CloudEventConsumer {
    @Autowired
    private ServiceResponderManager serviceResponderManager;

    @Override
    public boolean shouldAccept(CloudEventData<?> cloudEvent) {
        return PortsUpdateEvent.class.getCanonicalName().equalsIgnoreCase(cloudEvent.getAttributes().getType());
    }

    @Override
    public Mono<Void> consume(CloudEventData<?> cloudEvent) {
        PortsUpdateEvent event = CloudEventSupport.unwrapData(cloudEvent, PortsUpdateEvent.class);
        if (event != null) {
            ServiceResponder responder = serviceResponderManager.getByUUID(event.getAppId());
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
