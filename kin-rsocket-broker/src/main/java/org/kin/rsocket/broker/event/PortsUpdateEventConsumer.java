package org.kin.rsocket.broker.event;

import io.cloudevents.CloudEvent;
import org.kin.rsocket.broker.RSocketEndpoint;
import org.kin.rsocket.broker.RSocketServiceManager;
import org.kin.rsocket.core.event.AbstractCloudEventConsumer;
import org.kin.rsocket.core.event.PortsUpdateEvent;
import org.kin.rsocket.core.metadata.AppMetadata;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author huangjianqin
 * @date 2021/3/30
 */
public final class PortsUpdateEventConsumer extends AbstractCloudEventConsumer<PortsUpdateEvent> {
    @Autowired
    private RSocketServiceManager serviceManager;

    @Override
    public void consume(CloudEvent cloudEvent, PortsUpdateEvent event) {
        RSocketEndpoint rsocketEndpoint = serviceManager.getByUUID(event.getAppId());
        if (rsocketEndpoint != null) {
            AppMetadata appMetadata = rsocketEndpoint.getAppMetadata();
            appMetadata.updateWebPort(event.getWebPort());
            appMetadata.updateManagementPort(event.getManagementPort());
            appMetadata.updateRSocketPorts(event.getRSocketPorts());
        }
    }
}
