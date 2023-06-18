package org.kin.rsocket.core.transport;

import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.handler.ssl.util.SimpleTrustManagerFactory;
import io.rsocket.transport.ClientTransport;
import io.rsocket.transport.ServerTransport;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.transport.netty.server.TcpServerTransport;
import org.kin.framework.utils.ExceptionUtils;
import org.kin.rsocket.core.utils.Schemas;
import reactor.netty.tcp.TcpClient;
import reactor.netty.tcp.TcpServer;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.*;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * uri参数详情:
 * client:
 * fingerPrints:   指纹文件路径, 默认{user.home}/.rsocket/known_finger_prints
 * <p>
 * server:
 * keyStoreType: key store类型
 * keyStorePassword:   key store私钥密码, 默认{@link TlsTransportParser#DEFAULT_PASSWORD}
 * keyStore:  key store文件路径, 默认{user.home}/.rsocket/rsocket.p12
 *
 * @author huangjianqin
 * @date 2021/3/27
 */
public final class TlsTransportParser implements Uri2TransportParser {
    private static final List<String> SCHEMES = Arrays.asList(Schemas.TCPS, Schemas.TPC_TLS, Schemas.TLS);
    /** 使用的协议 */
    private static final String[] PROTOCOLS = new String[]{"TLSv1.3", "TLSv.1.2"};
    /** 默认key store密码 */
    private static final String DEFAULT_PASSWORD = "kin";

    @Override
    public Optional<ClientTransport> buildClient(URI uri) {
        Objects.requireNonNull(uri, "uri must not be null");

        if (!SCHEMES.contains(uri.getScheme())) {
            return Optional.empty();
        }

        try {
            Map<String, String> params = splitQuery(uri);

            TrustManagerFactory trustManagerFactory = InsecureTrustManagerFactory.INSTANCE;
            //读取指纹
            File fingerPrints = new File(params.getOrDefault("fingerPrints", System.getProperty("user.home") + "/.rsocket/known_finger_prints"));
            if (fingerPrints.exists()) {
                List<String> fingerPrintsSha256 = new ArrayList<>();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(Files.newInputStream(fingerPrints.toPath()), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (!line.isEmpty()) {
                            String fingerPrint = line.replaceAll(":", "");
                            fingerPrintsSha256.add(fingerPrint.trim());
                        }
                    }
                } catch (Exception ignore) {
                    //do nothing
                }
                if (!fingerPrintsSha256.isEmpty()) {
                    //构建trust manager
                    trustManagerFactory = new FingerPrintTrustManagerFactory(fingerPrintsSha256);
                }
            }

            SslContext context = SslContextBuilder
                    .forClient()
                    .protocols(PROTOCOLS)
                    .sslProvider(getSslProvider())
                    .trustManager(trustManagerFactory).build();
            TcpClient tcpClient = TcpClient.create()
                    .host(uri.getHost())
                    .port(uri.getPort())
                    .secure(ssl -> ssl.sslContext(context));
            return Optional.of(TcpClientTransport.create(tcpClient));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ServerTransport<?>> buildServer(URI uri) {
        Objects.requireNonNull(uri, "uri must not be null");
        if (!SCHEMES.contains(uri.getScheme())) {
            return Optional.empty();
        }
        try {
            Map<String, String> params = splitQuery(uri);
            PrivateKey privateKey;
            X509Certificate certificate;

            String keyStoreType = params.getOrDefault("keyStoreType", "PKCS12");
            char[] password = params.getOrDefault("keyStorePassword", DEFAULT_PASSWORD).toCharArray();
            File keyStore = new File(params.getOrDefault("keyStore", System.getProperty("user.home") + "/.rsocket/rsocket.p12"));
            if (keyStore.exists()) {
                // key store found
                KeyStore store = KeyStore.getInstance(keyStoreType);
                try (InputStream is = new FileInputStream(keyStore)) {
                    store.load(is, password);
                }
                String alias = store.aliases().nextElement();
                //证书
                certificate = (X509Certificate) store.getCertificate(alias);
                KeyStore.Entry entry = store.getEntry(alias, new KeyStore.PasswordProtection(password));
                //私钥
                privateKey = ((KeyStore.PrivateKeyEntry) entry).getPrivateKey();
            } else {
                // user netty self signed certification
                SelfSignedCertificate selfSignedCertificate = new SelfSignedCertificate();
                privateKey = selfSignedCertificate.key();
                certificate = selfSignedCertificate.cert();
            }
            TcpServer tcpServer = TcpServer.create()
                    .host(uri.getHost())
                    .port(uri.getPort())
                    .secure(ssl -> {
                        try {
                            ssl.sslContext(
                                    SslContextBuilder.forServer(privateKey, certificate)
                                            .protocols(PROTOCOLS)
                                            .sslProvider(getSslProvider())
                                            .build()
                            );
                        } catch (SSLException e) {
                            ExceptionUtils.throwExt(e);
                        }
                    });
            return Optional.of(TcpServerTransport.create(tcpServer));
        } catch (Exception ignore) {
            return Optional.empty();
        }
    }

    /**
     * @return ssl provider
     */
    private SslProvider getSslProvider() {
        if (OpenSsl.isAvailable()) {
            return SslProvider.OPENSSL_REFCNT;
        } else {
            return SslProvider.JDK;
        }
    }

    /**
     * 获取uri query参数
     */
    private Map<String, String> splitQuery(URI url) throws UnsupportedEncodingException {
        Map<String, String> queryPairs = new LinkedHashMap<>();
        String query = url.getQuery();
        if (query != null && !query.isEmpty()) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                queryPairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"), URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
            }
        }
        return queryPairs;
    }


    //----------------------------------------------------------------------------------------------------

    /**
     * 指纹管理
     */
    private static class FingerPrintTrustManagerFactory extends SimpleTrustManagerFactory {
        private final TrustManager trustManager;

        public FingerPrintTrustManagerFactory(List<String> fingerPrintsSha256) {
            this.trustManager = new FingerPrintX509TrustManager(fingerPrintsSha256);
        }

        @Override
        protected void engineInit(KeyStore keyStore) {

        }

        @Override
        protected void engineInit(ManagerFactoryParameters managerFactoryParameters) {

        }

        @Override
        protected TrustManager[] engineGetTrustManagers() {
            return new TrustManager[]{trustManager};
        }
    }
}
