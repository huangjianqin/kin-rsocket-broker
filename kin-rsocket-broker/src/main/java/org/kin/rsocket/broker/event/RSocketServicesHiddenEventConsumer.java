package org.kin.rsocket.broker.event;

import io.cloudevents.CloudEvent;
import org.kin.rsocket.broker.RSocketEndpoint;
import org.kin.rsocket.broker.RSocketServiceManager;
import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.event.AbstractCloudEventConsumer;
import org.kin.rsocket.core.event.RSocketServicesHiddenEvent;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

/**
 * @author huangjianqin
 * @date 2021/3/30
 */
public final class RSocketServicesHiddenEventConsumer extends AbstractCloudEventConsumer<RSocketServicesHiddenEvent> {
    @Autowired
    private RSocketServiceManager serviceManager;

    @Override
    public void consume(CloudEvent cloudEvent, RSocketServicesHiddenEvent event) {
        RSocketEndpoint rsocketEndpoint = serviceManager.getByUUID(event.getAppId());
        if (rsocketEndpoint != null) {
            Set<ServiceLocator> serviceLocators = event.getServices();
            rsocketEndpoint.unregisterServices(serviceLocators);
        }
    }
}
