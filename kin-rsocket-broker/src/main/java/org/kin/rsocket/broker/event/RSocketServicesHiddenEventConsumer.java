package org.kin.rsocket.broker.event;

import org.kin.rsocket.broker.RSocketEndpoint;
import org.kin.rsocket.broker.RSocketServiceManager;
import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.event.AbstractCloudEventConsumer;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.RSocketServicesExposedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * @author huangjianqin
 * @date 2021/3/30
 */
public final class RSocketServicesHiddenEventConsumer extends AbstractCloudEventConsumer<RSocketServicesExposedEvent> {
    @Autowired
    private RSocketServiceManager serviceManager;

    @Override
    public Mono<Void> consume(CloudEventData<?> cloudEventData, RSocketServicesExposedEvent event) {
        if (event != null && event.getAppId().equals(cloudEventData.getAttributes().getSource().getHost())) {
            RSocketEndpoint rsocketEndpoint = serviceManager.getByUUID(event.getAppId());
            if (rsocketEndpoint != null) {
                Set<ServiceLocator> serviceLocators = event.getServices();
                rsocketEndpoint.unregisterServices(serviceLocators);
            }
        }
        return Mono.empty();
    }
}
