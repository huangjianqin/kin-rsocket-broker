package org.kin.rsocket.broker;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.Collections;
import java.util.List;

/**
 * @author huangjianqin
 * @date 2021/2/15
 */
@ConfigurationProperties(prefix = "kin.rsocket.broker")
public class RSocketBrokerProperties {
    /** 监听端口 */
    private int port = 9999;
    /** topology: gossip, k8s, standalone */
    private String topology;
    /** 是否需要权限校验 */
    private boolean auth = true;
    /**
     * external domain for requester from external: the requester can not access broker's internal ip
     */
    private String externalDomain;
    /** ssl信息 */
    @NestedConfigurationProperty
    private RSocketSSL ssl;
    /** upstream broker url */
    private List<String> upstreamBrokers = Collections.emptyList();
    /** upstream token */
    private String upstreamToken;

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isAuth() {
        return auth;
    }

    public void setAuth(boolean auth) {
        this.auth = auth;
    }

    public String getExternalDomain() {
        return externalDomain;
    }

    public void setExternalDomain(String externalDomain) {
        this.externalDomain = externalDomain;
    }

    public String getTopology() {
        return topology;
    }

    public void setTopology(String topology) {
        this.topology = topology;
    }

    public RSocketSSL getSsl() {
        return ssl;
    }

    public void setSsl(RSocketSSL ssl) {
        this.ssl = ssl;
    }

    public List<String> getUpstreamBrokers() {
        return upstreamBrokers;
    }

    public void setUpstreamBrokers(List<String> upstreamBrokers) {
        this.upstreamBrokers = upstreamBrokers;
    }

    public String getUpstreamToken() {
        return upstreamToken;
    }

    public void setUpstreamToken(String upstreamToken) {
        this.upstreamToken = upstreamToken;
    }

    //-----------------------------------------------------------------------------------------------------------------
    public static class RSocketSSL {
        /**
         * todo
         */
        private boolean enabled = false;
        /**
         *
         */
        private String keyStoreType = "PKCS12";
        /**
         *
         */
        private String keyStore = System.getProperty("user.home") + "/.rsocket/rsocket.p12";
        /**
         *
         */
        private String keyStorePassword = "changeit";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getKeyStoreType() {
            return keyStoreType;
        }

        public void setKeyStoreType(String keyStoreType) {
            this.keyStoreType = keyStoreType;
        }

        public String getKeyStore() {
            return keyStore;
        }

        public void setKeyStore(String keyStore) {
            this.keyStore = keyStore;
        }

        public String getKeyStorePassword() {
            return keyStorePassword;
        }

        public void setKeyStorePassword(String keyStorePassword) {
            this.keyStorePassword = keyStorePassword;
        }
    }
}
