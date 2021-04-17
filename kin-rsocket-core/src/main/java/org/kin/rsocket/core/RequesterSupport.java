package org.kin.rsocket.core;

import io.rsocket.Payload;
import io.rsocket.SocketAcceptor;
import io.rsocket.plugins.RSocketInterceptor;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.ServicesExposedEvent;
import org.kin.rsocket.core.health.HealthCheck;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * RSocket requester support: setup payload, exposed services, acceptor, plugins
 *
 * @author huangjianqin
 * @date 2021/3/23
 */
public interface RequesterSupport {
    /**
     * 原始uri
     */
    URI originUri();

    /**
     * set up rsocket connector payload
     */
    Supplier<Payload> setupPayload();

    /**
     * @return exposed services信息
     */
    default Set<ServiceLocator> exposedServices() {
        return ReactiveServiceRegistry.INSTANCE.findAllServiceLocators()
                .stream()
                //过滤掉local service
                .filter(l -> !l.getService().equals(HealthCheck.class.getCanonicalName()))
                .collect(Collectors.toSet());
    }

    /**
     * @return services exposed cloud event
     */
    default CloudEventData<ServicesExposedEvent> servicesExposedEvent() {
        Collection<ServiceLocator> serviceLocators = exposedServices();
        if (serviceLocators.isEmpty()) {
            return null;
        }

        return ServicesExposedEvent.of(serviceLocators);
    }

    /**
     * @return rsocket connector acceptor
     */
    SocketAcceptor socketAcceptor();

    /**
     * @return rsocket connector responder interceptors
     */
    default List<RSocketInterceptor> responderInterceptors() {
        return Collections.emptyList();
    }

    /**
     * @return rsocket connector requester interceptors
     */
    default List<RSocketInterceptor> requesterInterceptors() {
        return Collections.emptyList();
    }
}
