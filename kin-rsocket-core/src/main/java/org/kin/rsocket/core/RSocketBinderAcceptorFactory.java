package org.kin.rsocket.core;

import io.rsocket.ConnectionSetupPayload;
import io.rsocket.RSocket;
import io.rsocket.SocketAcceptor;
import reactor.core.publisher.Mono;

/**
 * @author huangjianqin
 * @date 2021/3/27
 */
@FunctionalInterface
public interface RSocketBinderAcceptorFactory extends SocketAcceptor {
    /**
     * 返回responder rsocket server的acceptor
     */
    @Override
    Mono<RSocket> accept(ConnectionSetupPayload setup, RSocket sendingSocket);
}
