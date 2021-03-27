package org.kin.rsocket.core.transport;

import io.rsocket.transport.ClientTransport;
import io.rsocket.transport.ServerTransport;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.transport.netty.server.TcpServerTransport;
import org.kin.rsocket.core.utils.Schemas;
import reactor.netty.tcp.TcpServer;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * @author huangjianqin
 * @date 2021/3/27
 */
public final class TcpTransportParser implements Uri2TransportParser {
    @Override
    public Optional<ClientTransport> buildClient(URI uri) {
        Objects.requireNonNull(uri, "uri must not be null");

        if (!Schemas.TCP.equals(uri.getScheme())) {
            return Optional.empty();
        }

        return Optional.of(TcpClientTransport.create(uri.getHost(), uri.getPort()));
    }

    @Override
    public Optional<ServerTransport<?>> buildServer(URI uri) {
        Objects.requireNonNull(uri, "uri must not be null");

        if (!Schemas.TCP.equals(uri.getScheme())) {
            return Optional.empty();
        }

        return Optional.of(TcpServerTransport.create(TcpServer.create().host(uri.getHost()).port(uri.getPort())));
    }
}
