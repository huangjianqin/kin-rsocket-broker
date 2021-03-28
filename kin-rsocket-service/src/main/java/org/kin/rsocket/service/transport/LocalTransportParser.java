package org.kin.rsocket.service.transport;

import io.rsocket.transport.ClientTransport;
import io.rsocket.transport.ServerTransport;
import io.rsocket.transport.local.LocalClientTransport;
import io.rsocket.transport.local.LocalServerTransport;
import org.kin.rsocket.core.utils.Schemas;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * @author huangjianqin
 * @date 2021/3/27
 */
public final class LocalTransportParser implements Uri2TransportParser {
    @Override
    public Optional<ClientTransport> buildClient(URI uri) {
        Objects.requireNonNull(uri, "uri must not be null");

        if (!Schemas.LOCAL.equals(uri.getScheme())) {
            return Optional.empty();
        }

        return Optional.of(LocalClientTransport.create(uri.getSchemeSpecificPart()));
    }

    @Override
    public Optional<ServerTransport<?>> buildServer(URI uri) {
        Objects.requireNonNull(uri, "uri must not be null");

        if (!Schemas.LOCAL.equals(uri.getScheme())) {
            return Optional.empty();
        }

        return Optional.of(LocalServerTransport.create(uri.getSchemeSpecificPart()));
    }
}