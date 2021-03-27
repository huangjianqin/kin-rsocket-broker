package org.kin.rsocket.core.transport;

import io.rsocket.transport.ClientTransport;
import io.rsocket.transport.ServerTransport;
import org.kin.rsocket.core.RSocketAppContext;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 通过SPI加载支持的transport, 也就是{@link UriHandler}实现类
 *
 * @author huangjianqin
 * @date 2021/3/27
 */
public final class UriTransportRegistry {
    /** client端 找不到对应的transport */
    private static final ClientTransport FAILED_CLIENT_LOOKUP =
            () -> Mono.error(new UnsupportedOperationException());
    /** server端 找不到对应的transport */
    private static final ServerTransport<?> FAILED_SERVER_LOOKUP =
            (acceptor) -> Mono.error(new UnsupportedOperationException());
    /** 单例 */
    public static final UriTransportRegistry INSTANCE = new UriTransportRegistry();
    /** classpath中UriHandler实现类实例 */
    private List<UriHandler> handlers;

    private UriTransportRegistry() {
        List<UriHandler> extensions = RSocketAppContext.LOADER.getExtensions(UriHandler.class);
        handlers = Collections.unmodifiableList(extensions);
    }

    /**
     * 获取uri对应的client transport
     */
    public ClientTransport client(String uriString) {
        URI uri = URI.create(uriString);

        for (UriHandler h : handlers) {
            Optional<ClientTransport> r = h.buildClient(uri);
            if (r.isPresent()) {
                return r.get();
            }
        }

        return FAILED_CLIENT_LOOKUP;
    }

    /**
     * 获取uri对应的server transport
     */
    public ServerTransport<?> server(String uriString) {
        URI uri = URI.create(uriString);

        for (UriHandler h : handlers) {
            Optional<ServerTransport<?>> r = h.buildServer(uri);
            if (r.isPresent()) {
                return r.get();
            }
        }

        return FAILED_SERVER_LOOKUP;
    }
}
