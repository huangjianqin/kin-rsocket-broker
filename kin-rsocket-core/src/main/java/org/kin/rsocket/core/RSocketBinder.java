package org.kin.rsocket.core;

import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.rsocket.SocketAcceptor;
import io.rsocket.core.RSocketServer;
import io.rsocket.plugins.DuplexConnectionInterceptor;
import io.rsocket.plugins.RSocketInterceptor;
import io.rsocket.plugins.SocketAcceptorInterceptor;
import io.rsocket.transport.ServerTransport;
import io.rsocket.transport.local.LocalServerTransport;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.rsocket.transport.netty.server.WebsocketServerTransport;
import org.kin.framework.Closeable;
import org.kin.framework.utils.NetUtils;
import org.kin.rsocket.core.utils.Schemas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.netty.http.server.HttpServer;
import reactor.netty.tcp.TcpServer;

import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author huangjianqin
 * @date 2021/3/27
 */
public class RSocketBinder implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(RSocketBinder.class);
    private static final String[] PROTOCOLS = new String[]{"TLSv1.3", "TLSv.1.2"};

    /** bind的schema */
    private final Map<Integer, String> schemas = new HashMap<>();
    private String host = "0.0.0.0";
    /** 证书 */
    private Certificate certificate;
    /** 私钥 */
    private PrivateKey privateKey;
    private SocketAcceptor acceptor;
    private final List<RSocketInterceptor> responderInterceptors = new ArrayList<>();
    private final List<SocketAcceptorInterceptor> acceptorInterceptors = new ArrayList<>();
    private final List<DuplexConnectionInterceptor> connectionInterceptors = new ArrayList<>();
    //---------------------------------------------------------------------------------------------------------------------------------------
    private volatile boolean stopped;
    /** 已启动的{@link RSocketServer}对应的{@link Disposable} */
    private List<Disposable> responders;

    /**
     * bind
     */
    public void start() {
        if (!stopped) {
            responders = new ArrayList<>(schemas.size());
            for (Map.Entry<Integer, String> entry : schemas.entrySet()) {
                String schema = entry.getValue();
                int port = entry.getKey();
                if (!NetUtils.isPortInRange(port)) {
                    log.warn(String.format("schema '%s' port '%d' is invalid", schema, port));
                    continue;
                }
                ServerTransport<?> transport;
                if (schema.equals(Schemas.LOCAL)) {
                    transport = LocalServerTransport.create("unittest");
                } else if (schema.equals(Schemas.TCP)) {
                    transport = TcpServerTransport.create(host, port);
                } else if (schema.equals(Schemas.TCPS)) {
                    TcpServer tcpServer = TcpServer.create()
                            .host(host)
                            .port(port)
                            .secure(ssl -> ssl.sslContext(
                                    SslContextBuilder.forServer(privateKey, (X509Certificate) certificate)
                                            .protocols(PROTOCOLS)
                                            .sslProvider(getSslProvider())
                            ));
                    transport = TcpServerTransport.create(tcpServer);
                } else if (schema.equals(Schemas.WS)) {
                    transport = WebsocketServerTransport.create(host, port);
                } else if (schema.equals(Schemas.WSS)) {
                    HttpServer httpServer = HttpServer.create()
                            .host(host)
                            .port(port)
                            .secure(ssl -> ssl.sslContext(
                                    SslContextBuilder.forServer(privateKey, (X509Certificate) certificate)
                                            .protocols(PROTOCOLS)
                                            .sslProvider(getSslProvider())
                            ));
                    transport = WebsocketServerTransport.create(httpServer);
                } else {
                    log.warn(String.format("unknown schema '%s', just retry to bind with tcp", schema));
                    //回退到默认tcp
                    transport = TcpServerTransport.create(host, port);
                }

                RSocketServer rsocketServer = RSocketServer.create();
                //acceptor interceptor
                for (SocketAcceptorInterceptor acceptorInterceptor : acceptorInterceptors) {
                    rsocketServer.interceptors(interceptorRegistry -> {
                        interceptorRegistry.forSocketAcceptor(acceptorInterceptor);
                    });
                }
                //connection interceptor
                for (DuplexConnectionInterceptor connectionInterceptor : connectionInterceptors) {
                    rsocketServer.interceptors(interceptorRegistry -> {
                        interceptorRegistry.forConnection(connectionInterceptor);
                    });

                }
                //responder interceptor
                for (RSocketInterceptor responderInterceptor : responderInterceptors) {
                    rsocketServer.interceptors(interceptorRegistry -> {
                        interceptorRegistry.forResponder(responderInterceptor);
                    });
                }

                Disposable disposable = rsocketServer
                        .acceptor(acceptor)
                        .bind(transport)
                        .onTerminateDetach()
                        .subscribe();
                responders.add(disposable);
                log.info("succeed to start RSocket on " + schema + "://" + host + ":" + port);

                RSocketAppContext.rsocketPorts = schemas;
            }
        }
    }

    /**
     * close
     */
    @Override
    public void close() {
        stopped = true;
        for (Disposable responder : responders) {
            responder.dispose();
        }
    }

    /**
     * 获取ssl provider
     */
    private SslProvider getSslProvider() {
        if (OpenSsl.isAvailable()) {
            return SslProvider.OPENSSL_REFCNT;
        } else {
            return SslProvider.JDK;
        }
    }

    //------------------------------------------------------------------------------------------------------------
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private RSocketBinder binder = new RSocketBinder();

        private Builder() {

        }

        public RSocketBinder.Builder host(String host) {
            binder.host = host;
            return this;
        }

        public RSocketBinder.Builder listen(String schema, int port) {
            binder.schemas.put(port, schema);
            return this;
        }

        public RSocketBinder.Builder sslContext(Certificate certificate, PrivateKey privateKey) {
            binder.certificate = certificate;
            binder.privateKey = privateKey;
            return this;
        }

        public RSocketBinder.Builder addResponderInterceptor(RSocketInterceptor interceptor) {
            binder.responderInterceptors.add(interceptor);
            return this;
        }

        public RSocketBinder.Builder addSocketAcceptorInterceptor(SocketAcceptorInterceptor interceptor) {
            binder.acceptorInterceptors.add(interceptor);
            return this;
        }

        public RSocketBinder.Builder addConnectionInterceptor(DuplexConnectionInterceptor interceptor) {
            binder.connectionInterceptors.add(interceptor);
            return this;
        }

        public RSocketBinder.Builder acceptor(SocketAcceptor acceptor) {
            binder.acceptor = acceptor;
            return this;
        }

        public RSocketBinder build() {
            return binder;
        }
    }
}
