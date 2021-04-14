package org.kin.rsocket.broker.event;

import org.kin.rsocket.broker.ServiceResponder;
import org.kin.rsocket.broker.ServiceResponderManager;
import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.event.CloudEventConsumer;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.CloudEventSupport;
import org.kin.rsocket.core.event.ServicesHiddenEvent;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * @author huangjianqin
 * @date 2021/3/30
 */
public final class ServicesHiddenEventConsumer implements CloudEventConsumer {
    @Autowired
    private ServiceResponderManager serviceResponderManager;

    @Override
    public boolean shouldAccept(CloudEventData<?> cloudEvent) {
        return ServicesHiddenEvent.class.getCanonicalName().equalsIgnoreCase(cloudEvent.getAttributes().getType());
    }

    @Override
    public Mono<Void> consume(CloudEventData<?> cloudEvent) {
        ServicesHiddenEvent event = CloudEventSupport.unwrapData(cloudEvent, ServicesHiddenEvent.class);
        if (event != null && event.getAppId().equals(cloudEvent.getAttributes().getSource().getHost())) {
            ServiceResponder responder = serviceResponderManager.getByUUID(event.getAppId());
            if (responder != null) {
                Set<ServiceLocator> serviceLocators = event.getServices();
                responder.unregisterServices(serviceLocators);
            }
        }
        return Mono.empty();
    }
}
