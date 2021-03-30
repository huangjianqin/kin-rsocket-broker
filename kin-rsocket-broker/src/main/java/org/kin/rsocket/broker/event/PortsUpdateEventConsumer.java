package org.kin.rsocket.broker.event;

import org.kin.rsocket.broker.ServiceResponder;
import org.kin.rsocket.broker.ServiceRouter;
import org.kin.rsocket.core.event.CloudEventConsumer;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.CloudEventSupport;
import org.kin.rsocket.core.event.broker.PortsUpdateEvent;
import org.kin.rsocket.core.metadata.AppMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;

/**
 * @author huangjianqin
 * @date 2021/3/30
 */
public class PortsUpdateEventConsumer implements CloudEventConsumer {
    @Autowired
    private ServiceRouter serviceRouter;

    @Override
    public boolean shouldAccept(CloudEventData<?> cloudEvent) {
        return PortsUpdateEvent.class.getCanonicalName().equalsIgnoreCase(cloudEvent.getAttributes().getType());
    }

    @Override
    public Mono<Void> consume(CloudEventData<?> cloudEvent) {
        PortsUpdateEvent portsUpdateEvent = CloudEventSupport.unwrapData(cloudEvent, PortsUpdateEvent.class);
        if (portsUpdateEvent != null) {
            ServiceResponder responder = serviceRouter.getByUUID(portsUpdateEvent.getAppId());
            if (responder != null) {
                AppMetadata appMetadata = responder.getAppMetadata();
                appMetadata.setWebPort(portsUpdateEvent.getWebPort());
                appMetadata.setManagementPort(portsUpdateEvent.getManagementPort());
                appMetadata.setRsocketPorts(portsUpdateEvent.getRsocketPorts());
            }
        }
        return Mono.empty();
    }
}
