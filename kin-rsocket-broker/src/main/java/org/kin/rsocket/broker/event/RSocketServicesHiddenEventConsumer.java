package org.kin.rsocket.broker.event;

import io.cloudevents.CloudEvent;
import org.kin.rsocket.broker.RSocketService;
import org.kin.rsocket.broker.RSocketServiceRegistry;
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
    private RSocketServiceRegistry serviceRegistry;

    @Override
    public void consume(CloudEvent cloudEvent, RSocketServicesHiddenEvent event) {
        RSocketService rsocketService = serviceRegistry.getByUUID(event.getAppId());
        if (rsocketService != null) {
            Set<ServiceLocator> serviceLocators = event.getServices();
            rsocketService.unregisterServices(serviceLocators);
        }
    }
}
