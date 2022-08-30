package org.kin.rsocket.gateway.http;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.File;

/**
 * @author huangjianqin
 * @date 2021/4/20
 */
@ConfigurationProperties(prefix = "kin.rsocket.gateway")
public class RSocketBrokerHttpGatewayProperties {
    /** http gateway是否开启api校验 */
    private boolean restApiAuth = false;
    /** 校验token文件目录 */
    private String authDir = System.getProperty("user.home").concat(File.separator).concat(".rsocket");

    //setter && getter
    public boolean isRestApiAuth() {
        return restApiAuth;
    }

    public void setRestApiAuth(boolean restApiAuth) {
        this.restApiAuth = restApiAuth;
    }

    public String getAuthDir() {
        return authDir;
    }

    public void setAuthDir(String authDir) {
        this.authDir = authDir;
    }
}
