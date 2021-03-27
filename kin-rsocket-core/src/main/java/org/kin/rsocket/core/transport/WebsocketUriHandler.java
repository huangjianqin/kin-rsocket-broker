package org.kin.rsocket.core.transport;

import io.rsocket.transport.ClientTransport;
import io.rsocket.transport.ServerTransport;
import io.rsocket.transport.netty.client.WebsocketClientTransport;
import io.rsocket.transport.netty.server.WebsocketServerTransport;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @author huangjianqin
 * @date 2021/3/27
 */
public final class WebsocketUriHandler implements UriHandler {
    private static final List<String> SCHEME = Arrays.asList("ws", "wss", "http", "https");

    @Override
    public Optional<ClientTransport> buildClient(URI uri) {
        Objects.requireNonNull(uri, "uri must not be null");

        if (SCHEME.stream().noneMatch(scheme -> scheme.equals(uri.getScheme()))) {
            return Optional.empty();
        }

        return Optional.of(WebsocketClientTransport.create(uri));
    }

    @Override
    public Optional<ServerTransport<?>> buildServer(URI uri) {
        Objects.requireNonNull(uri, "uri must not be null");

        if (SCHEME.stream().noneMatch(scheme -> scheme.equals(uri.getScheme()))) {
            return Optional.empty();
        }

        //根据是否是secure schema, 决定使用不同的端口
        int port = isSecure(uri) ? getPort(uri, 443) : getPort(uri, 80);

        return Optional.of(WebsocketServerTransport.create(uri.getHost(), port));
    }

    /**
     * 返回uri对应的端口
     */
    public static int getPort(URI uri, int defaultPort) {
        Objects.requireNonNull(uri, "uri must not be null");
        return uri.getPort() == -1 ? defaultPort : uri.getPort();
    }

    /**
     * 判断uri是否是secure schema
     */
    public static boolean isSecure(URI uri) {
        Objects.requireNonNull(uri, "uri must not be null");
        return uri.getScheme().equals("wss") || uri.getScheme().equals("https");
    }
}