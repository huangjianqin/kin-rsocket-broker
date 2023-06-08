package org.kin.rsocket.core;

import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.rsocket.Closeable;
import io.rsocket.ConnectionSetupPayload;
import io.rsocket.RSocket;
import io.rsocket.SocketAcceptor;
import io.rsocket.frame.decoder.PayloadDecoder;
import io.rsocket.plugins.DuplexConnectionInterceptor;
import io.rsocket.plugins.RSocketInterceptor;
import io.rsocket.plugins.RequestInterceptor;
import io.rsocket.plugins.SocketAcceptorInterceptor;
import io.rsocket.transport.ServerTransport;
import io.rsocket.transport.local.LocalServerTransport;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.rsocket.transport.netty.server.WebsocketServerTransport;
import org.eclipse.collections.impl.map.mutable.UnifiedMap;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 绑定rsocket端口, 监听rsocket协议
 *
 * @author huangjianqin
 * @date 2021/3/27
 */
public class RSocketServer implements Disposable {
    private static final Logger log = LoggerFactory.getLogger(RSocketServer.class);
    private static final String[] PROTOCOLS = new String[]{"TLSv1.3", "TLSv.1.2"};

    //状态-初始
    private static final int STATE_INIT = 0;
    //状态-已启动
    private static final int STATE_START = 1;
    //状态-已closed
    private static final int STATE_TERMINATED = 2;

    /** bind的schema */
    private final Map<Integer, String> schemas = new UnifiedMap<>();
    private String host = "0.0.0.0";
    /** 证书 */
    private Certificate certificate;
    /** 私钥 */
    private PrivateKey privateKey;
    /** rsocket client connect setup逻辑 */
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
    /** 状态 */
    private final AtomicInteger state = new AtomicInteger(STATE_INIT);
    /** 已启动的{@link io.rsocket.core.RSocketServer}对应的{@link Disposable} */
    private List<Disposable> closeableChannels = new CopyOnWriteArrayList<>();

    /**
     * bind
     */
    public void bind() {
        if (!state.compareAndSet(STATE_INIT, STATE_START)) {
            return;
        }

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

            io.rsocket.core.RSocketServer rsocketServer = io.rsocket.core.RSocketServer.create();
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

            rsocketServer
                    .acceptor(acceptor)
                    //zero copy
                    .payloadDecoder(PayloadDecoder.ZERO_COPY)
                    .bind(transport)
                    .onTerminateDetach()
                    .subscribe(cc -> {
                        log.info("succeed to start rsocket server on " + schema + "://" + host + ":" + port);
                        onBound(cc);
                    });

            RSocketAppContext.rsocketPorts = schemas;
        }
    }

    /**
     * process after server bound
     */
    private void onBound(Closeable closeableChannel) {
        if (!isDisposed()) {
            //未disposed
            closeableChannels.add(closeableChannel);
        } else {
            //已disposed
            closeableChannel.dispose();
        }
    }

    @Override
    public void dispose() {
        if (!state.compareAndSet(STATE_START, STATE_TERMINATED)) {
            return;
        }

        for (Disposable disposable : closeableChannels) {
            if (disposable.isDisposed()) {
                continue;
            }

            disposable.dispose();
        }
    }

    @Override
    public boolean isDisposed() {
        return state.get() == STATE_TERMINATED;
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

    //--------------------------------------------------------builder
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final RSocketServer rsocketServer = new RSocketServer();

        private Builder() {

        }

        public RSocketServer.Builder host(String host) {
            rsocketServer.host = host;
            return this;
        }

        public RSocketServer.Builder listen(String schema, int port) {
            rsocketServer.schemas.put(port, schema);
            return this;
        }

        public RSocketServer.Builder sslContext(Certificate certificate, PrivateKey privateKey) {
            rsocketServer.certificate = certificate;
            rsocketServer.privateKey = privateKey;
            return this;
        }

        /**
         * 可以拦截并将requester rsocket转换成别的{@link RSocket}实现, {@link SocketAcceptor#accept(ConnectionSetupPayload, RSocket)}中第二个参数就是该{@link RSocket}实现
         * 但在{@link RequestInterceptor}却观察不到这个变化
         */
        public RSocketServer.Builder addRequesterInterceptors(RSocketInterceptor interceptor) {
            rsocketServer.requesterInterceptors.add(interceptor);
            return this;
        }

        /**
         * 可以拦截并将responder rsocket转换成别的{@link RSocket}实现, 该responder rsocket就是{@link SocketAcceptor#accept(ConnectionSetupPayload, RSocket)}的返回值
         */
        public RSocketServer.Builder addResponderInterceptor(RSocketInterceptor interceptor) {
            rsocketServer.responderInterceptors.add(interceptor);
            return this;
        }

        public RSocketServer.Builder addSocketAcceptorInterceptor(SocketAcceptorInterceptor interceptor) {
            rsocketServer.acceptorInterceptors.add(interceptor);
            return this;
        }

        public RSocketServer.Builder addConnectionInterceptor(DuplexConnectionInterceptor interceptor) {
            rsocketServer.connectionInterceptors.add(interceptor);
            return this;
        }

        public RSocketServer.Builder addRequesterRequestInterceptors(Function<RSocket, ? extends RequestInterceptor> interceptor) {
            rsocketServer.requesterRequestInterceptors.add(interceptor);
            return this;
        }

        public RSocketServer.Builder addResponderRequestInterceptor(Function<RSocket, ? extends RequestInterceptor> interceptor) {
            rsocketServer.responderRequestInterceptors.add(interceptor);
            return this;
        }

        public RSocketServer.Builder acceptor(SocketAcceptor acceptor) {
            rsocketServer.acceptor = acceptor;
            return this;
        }

        public RSocketServer build() {
            return rsocketServer;
        }
    }
}
