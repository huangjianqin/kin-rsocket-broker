package org.kin.rsocket.broker;

import org.apache.commons.io.FileUtils;
import org.kin.framework.utils.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
    /** 是否需要权限校验 */
    private boolean auth = true;
    /** 校验token文件 */
    private String authDir = System.getProperty("user.home").concat(".rsocket");
    /** external domain for requester from external: the requester can not access broker's internal ip */
    private String externalDomain = "";
    /** ssl信息 */
    @NestedConfigurationProperty
    private RSocketSSL ssl;
    /** upstream broker url */
    private List<String> upstreamBrokers = Collections.emptyList();
    /**
     * upstream token
     * 支持文件路径配置
     * 目前仅仅支持jwt
     */
    private String upstreamToken;
    /** broker 路由规则 */
    private String route;

    @PostConstruct
    public void loadUpstreamToken() throws IOException {
        if (StringUtils.isBlank(upstreamToken)) {
            return;
        }

        File upstreamTokenFile = new File(upstreamToken);
        if (!upstreamTokenFile.exists() || upstreamTokenFile.isDirectory()) {
            return;
        }

        //如果配置的jwt token文件路径, 则加载进来并覆盖
        upstreamToken = FileUtils.readFileToString(upstreamTokenFile, StandardCharsets.UTF_8);
    }

    //setter && getter
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

    public String getAuthDir() {
        return authDir;
    }

    public void setAuthDir(String authDir) {
        this.authDir = authDir;
    }

    public String getExternalDomain() {
        return externalDomain;
    }

    public void setExternalDomain(String externalDomain) {
        this.externalDomain = externalDomain;
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

    public String getRoute() {
        return route;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    //-----------------------------------------------------------------------------------------------------------------
    public static class RSocketSSL {
        /** 是否开启tcp ssl */
        private boolean enabled = false;
        /** 密钥类型 */
        private String keyStoreType = "PKCS12";
        /** key store路径 */
        private String keyStore = System.getProperty("user.home") + "/.rsocket/rsocket.p12";
        /** 默认key store密钥密码 */
        private String keyStorePassword = "kin";

        //setter && getter
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
