package org.kin.rsocket.core;

import io.rsocket.Payload;
import io.rsocket.SocketAcceptor;
import io.rsocket.plugins.RSocketInterceptor;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.broker.ServicesExposedEvent;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

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
    Supplier<Set<ServiceLocator>> exposedServices();

    /**
     * @return subscribed services信息
     */
    Supplier<Set<ServiceLocator>> subscribedServices();

    /**
     * @return services exposed cloud event
     */
    Supplier<CloudEventData<ServicesExposedEvent>> servicesExposedEvent();

    /**
     * @return rsocket connector acceptor
     */
    SocketAcceptor socketAcceptor();

    /**
     * @return rsocket connector responder interceptors
     */
    List<RSocketInterceptor> responderInterceptors();

    /**
     * @return rsocket connector requester interceptors
     */
    List<RSocketInterceptor> requesterInterceptors();
}
