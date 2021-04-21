package org.kin.rsocket.broker.event;

import org.kin.rsocket.broker.BrokerResponder;
import org.kin.rsocket.broker.ServiceManager;
import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.event.AbstractCloudEventConsumer;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.ServicesExposedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * @author huangjianqin
 * @date 2021/3/30
 */
public final class ServicesHiddenEventConsumer extends AbstractCloudEventConsumer<ServicesExposedEvent> {
    @Autowired
    private ServiceManager serviceManager;

    @Override
    public Mono<Void> consume(CloudEventData<?> cloudEventData, ServicesExposedEvent event) {
        if (event != null && event.getAppId().equals(cloudEventData.getAttributes().getSource().getHost())) {
            BrokerResponder responder = serviceManager.getByUUID(event.getAppId());
            if (responder != null) {
                Set<ServiceLocator> serviceLocators = event.getServices();
                responder.unregisterServices(serviceLocators);
            }
        }
        return Mono.empty();
    }
}
