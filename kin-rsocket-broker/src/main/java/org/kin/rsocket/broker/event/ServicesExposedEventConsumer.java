package org.kin.rsocket.broker.event;

import org.kin.rsocket.broker.ServiceResponder;
import org.kin.rsocket.broker.ServiceRouter;
import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.domain.AppStatus;
import org.kin.rsocket.core.event.CloudEventConsumer;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.CloudEventSupport;
import org.kin.rsocket.core.event.ServicesExposedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * @author huangjianqin
 * @date 2021/3/30
 */
public class ServicesExposedEventConsumer implements CloudEventConsumer {
    @Autowired
    private ServiceRouter serviceRouter;

    @Override
    public boolean shouldAccept(CloudEventData<?> cloudEvent) {
        return ServicesExposedEvent.class.getCanonicalName().equalsIgnoreCase(cloudEvent.getAttributes().getType());
    }

    @Override
    public Mono<Void> consume(CloudEventData<?> cloudEvent) {
        ServicesExposedEvent event = CloudEventSupport.unwrapData(cloudEvent, ServicesExposedEvent.class);
        if (event != null && event.getAppId().equals(cloudEvent.getAttributes().getSource().getHost())) {
            ServiceResponder responder = serviceRouter.getByUUID(event.getAppId());
            if (responder != null) {
                Set<ServiceLocator> serviceLocators = event.getServices();
                responder.setAppStatus(AppStatus.SERVING);
                responder.registerServices(serviceLocators);
            }
        }
        return Mono.empty();
    }
}
