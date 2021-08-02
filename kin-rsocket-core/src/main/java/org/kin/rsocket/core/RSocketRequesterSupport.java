package org.kin.rsocket.core;

import io.rsocket.Payload;
import io.rsocket.SocketAcceptor;
import io.rsocket.plugins.RSocketInterceptor;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * RSocket requester support: setup payload, exposed services, acceptor, plugins
 *
 * @author huangjianqin
 * @date 2021/3/23
 */
public interface RSocketRequesterSupport {
    /**
     * 原始uri
     */
    URI originUri();

    /**
     * set up rsocket connector payload
     */
    Supplier<Payload> setupPayload();

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
