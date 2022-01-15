package org.kin.rsocket.core;

import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.rsocket.RSocket;
import io.rsocket.SocketAcceptor;
import io.rsocket.core.RSocketServer;
import io.rsocket.frame.decoder.PayloadDecoder;
import io.rsocket.plugins.DuplexConnectionInterceptor;
import io.rsocket.plugins.RSocketInterceptor;
import io.rsocket.plugins.RequestInterceptor;
import io.rsocket.plugins.SocketAcceptorInterceptor;
import io.rsocket.transport.ServerTransport;
import io.rsocket.transport.local.LocalServerTransport;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.rsocket.transport.netty.server.WebsocketServerTransport;
import org.kin.framework.Closeable;
import org.kin.framework.utils.ExceptionUtils;
import org.kin.framework.utils.NetUtils;
import org.kin.rsocket.core.utils.Schemas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.netty.http.server.HttpServer;
import reactor.netty.tcp.TcpServer;

import javax.net.ssl.SSLException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

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
    /** @see io.rsocket.plugins.InterceptorRegistry#forRequester(RSocketInterceptor) */
    private final List<RSocketInterceptor> requesterInterceptors = new ArrayList<>();
    /** @see io.rsocket.plugins.InterceptorRegistry#forResponder(RSocketInterceptor) */
    private final List<RSocketInterceptor> responderInterceptors = new ArrayList<>();
    /** @see io.rsocket.plugins.InterceptorRegistry#forSocketAcceptor(SocketAcceptorInterceptor) */
    private final List<SocketAcceptorInterceptor> acceptorInterceptors = new ArrayList<>();
    /** @see io.rsocket.plugins.InterceptorRegistry#forConnection(Consumer) */
    private final List<DuplexConnectionInterceptor> connectionInterceptors = new ArrayList<>();
    /** @see io.rsocket.plugins.InterceptorRegistry#forRequestsInRequester(Function) */
    private final List<Function<RSocket, ? extends RequestInterceptor>> requesterRequestInterceptors = new ArrayList<>();
    /** @see io.rsocket.plugins.InterceptorRegistry#forRequestsInResponder(Function) */
    private final List<Function<RSocket, ? extends RequestInterceptor>> responderRequestInterceptors = new ArrayList<>();
    //---------------------------------------------------------------------------------------------------------------------------------------
    private volatile boolean stopped;
    /** 已启动的{@link RSocketServer}对应的{@link Disposable} */
    private List<Disposable> responders;

    /**
     * bind
     */
    public void bind() {
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
                switch (schema) {
                    case Schemas.LOCAL:
                        transport = LocalServerTransport.create("unittest");
                        break;
                    case Schemas.TCP:
                        transport = TcpServerTransport.create(host, port);
                        break;
                    case Schemas.TCPS:
                        TcpServer tcpServer = TcpServer.create()
                                .host(host)
                                .port(port)
                                .secure(ssl -> {
                                    try {
                                        ssl.sslContext(
                                                SslContextBuilder.forServer(privateKey, (X509Certificate) certificate)
                                                        .protocols(PROTOCOLS)
                                                        .sslProvider(getSslProvider())
                                                        .build()
                                        );
                                    } catch (SSLException e) {
                                        ExceptionUtils.throwExt(e);
                                    }
                                });
                        transport = TcpServerTransport.create(tcpServer);
                        break;
                    case Schemas.WS:
                        transport = WebsocketServerTransport.create(host, port);
                        break;
                    case Schemas.WSS:
                        HttpServer httpServer = HttpServer.create()
                                .host(host)
                                .port(port)
                                .secure(ssl -> {
                                    try {
                                        ssl.sslContext(
                                                SslContextBuilder.forServer(privateKey, (X509Certificate) certificate)
                                                        .protocols(PROTOCOLS)
                                                        .sslProvider(getSslProvider())
                                                        .build()
                                        );
                                    } catch (SSLException e) {
                                        ExceptionUtils.throwExt(e);
                                    }
                                });
                        transport = WebsocketServerTransport.create(httpServer);
                        break;
                    default:
                        log.warn(String.format("unknown schema '%s', just retry to bind with tcp", schema));
                        //回退到默认tcp
                        transport = TcpServerTransport.create(host, port);
                        break;
                }

                RSocketServer rsocketServer = RSocketServer.create();
                //acceptor interceptor
                for (SocketAcceptorInterceptor interceptor : acceptorInterceptors) {
                    rsocketServer.interceptors(interceptorRegistry -> interceptorRegistry.forSocketAcceptor(interceptor));
                }

                //connection interceptor
                for (DuplexConnectionInterceptor interceptor : connectionInterceptors) {
                    rsocketServer.interceptors(interceptorRegistry -> interceptorRegistry.forConnection(interceptor));

                }

                //requester interceptor
                for (RSocketInterceptor interceptor : requesterInterceptors) {
                    rsocketServer.interceptors(interceptorRegistry -> interceptorRegistry.forRequester(interceptor));
                }

                //responder interceptor
                for (RSocketInterceptor interceptor : responderInterceptors) {
                    rsocketServer.interceptors(interceptorRegistry -> interceptorRegistry.forResponder(interceptor));
                }

                //request interceptor in request
                for (Function<RSocket, ? extends RequestInterceptor> interceptor : requesterRequestInterceptors) {
                    rsocketServer.interceptors(interceptorRegistry -> interceptorRegistry.forRequestsInRequester(interceptor));
                }

                //request interceptor in responder
                for (Function<RSocket, ? extends RequestInterceptor> interceptor : responderRequestInterceptors) {
                    rsocketServer.interceptors(interceptorRegistry -> interceptorRegistry.forRequestsInResponder(interceptor));
                }

                Disposable disposable = rsocketServer
                        .acceptor(acceptor)
                        //zero copy
                        .payloadDecoder(PayloadDecoder.ZERO_COPY)
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
        private final RSocketBinder binder = new RSocketBinder();

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

        public RSocketBinder.Builder addRequesterInterceptors(RSocketInterceptor interceptor) {
            binder.requesterInterceptors.add(interceptor);
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

        public RSocketBinder.Builder addRequesterRequestInterceptors(Function<RSocket, ? extends RequestInterceptor> interceptor) {
            binder.requesterRequestInterceptors.add(interceptor);
            return this;
        }

        public RSocketBinder.Builder addResponderRequestInterceptor(Function<RSocket, ? extends RequestInterceptor> interceptor) {
            binder.responderRequestInterceptors.add(interceptor);
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
