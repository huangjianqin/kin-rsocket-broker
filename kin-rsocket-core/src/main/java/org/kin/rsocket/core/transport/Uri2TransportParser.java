package org.kin.rsocket.core.transport;

import io.rsocket.transport.ClientTransport;
import io.rsocket.transport.ServerTransport;
import org.kin.framework.utils.SPI;

import java.net.URI;
import java.util.Optional;

/**
 * {@link URI} <-> {@link ClientTransport} or {@link ServerTransport}的映射
 * 根据uri的schema
 *
 * @author huangjianqin
 * @date 2021/3/27
 */
@SPI(alias = "uri2TransportParser")
public interface Uri2TransportParser {
    /**
     * 返回适配{@link URI}的{@link ClientTransport}
     */
    Optional<ClientTransport> buildClient(URI uri);

    /**
     * 返回适配{@link URI}的{@link ServerTransport}
     */
    Optional<ServerTransport<?>> buildServer(URI uri);
}
