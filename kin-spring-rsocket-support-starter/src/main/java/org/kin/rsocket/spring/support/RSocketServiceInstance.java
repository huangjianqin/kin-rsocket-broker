package org.kin.rsocket.spring.support;

import io.rsocket.transport.ClientTransport;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.transport.netty.client.WebsocketClientTransport;

import java.net.URI;
import java.util.Objects;

/**
 * naming service上注册的rsocket service instance信息
 *
 * @author huangjianqin
 * @date 2022/3/15
 */
public class RSocketServiceInstance {
    private final String host;
    private final int port;
    /** rsocket schema, such as tcp, ws, wss */
    private final String schema;
    /** path, for websocket only */
    private String path;

    public RSocketServiceInstance(String host, int port, String schema) {
        this.host = host;
        this.port = port;
        this.schema = schema;
    }

    /**
     * 是否是websocket schema
     */
    public boolean isWebSocket() {
        return "ws".equals(this.schema) || "wss".equals(this.schema);
    }

    /**
     * 获取rsocket service instance的uri形式
     */
    public String getURI() {
        if (isWebSocket()) {
            return schema + "://" + host + ":" + port + path;
        } else {
            return schema + "://" + host + ":" + port;
        }
    }

    /**
     * 根据rsocket service instance信息, 转换成{@link ClientTransport}实例
     */
    public ClientTransport toClientTransport() {
        if (this.isWebSocket()) {
            return WebsocketClientTransport.create(URI.create(getURI()));
        }
        return TcpClientTransport.create(host, port);
    }

    /**
     * 更新websocket握手path
     */
    public void updateWsPath(String path) {
        this.path = path;
    }

    //getter
    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getSchema() {
        return schema;
    }

    public String getPath() {
        return path;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RSocketServiceInstance)) {
            return false;
        }
        RSocketServiceInstance that = (RSocketServiceInstance) o;
        return port == that.port && Objects.equals(host, that.host);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port);
    }
}
