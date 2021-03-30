package org.kin.rsocket.broker.event;

import org.kin.rsocket.broker.ServiceResponder;
import org.kin.rsocket.broker.ServiceRouter;
import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.event.CloudEventConsumer;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.CloudEventSupport;
import org.kin.rsocket.core.event.broker.ServicesHiddenEvent;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * @author huangjianqin
 * @date 2021/3/30
 */
public class ServicesHiddenEventConsumer implements CloudEventConsumer {
    @Autowired
    private ServiceRouter serviceRouter;

    @Override
    public boolean shouldAccept(CloudEventData<?> cloudEvent) {
        return ServicesHiddenEvent.class.getCanonicalName().equalsIgnoreCase(cloudEvent.getAttributes().getType());
    }

    @Override
    public Mono<Void> consume(CloudEventData<?> cloudEvent) {
        ServicesHiddenEvent servicesHiddenEvent = CloudEventSupport.unwrapData(cloudEvent, ServicesHiddenEvent.class);
        if (servicesHiddenEvent != null && servicesHiddenEvent.getAppId().equals(cloudEvent.getAttributes().getSource().getHost())) {
            ServiceResponder responder = serviceRouter.getByUUID(servicesHiddenEvent.getAppId());
            if (responder != null) {
                Set<ServiceLocator> serviceLocators = servicesHiddenEvent.getServices();
                responder.unregisterServices(serviceLocators);
            }
        }
        return Mono.empty();
    }
}
