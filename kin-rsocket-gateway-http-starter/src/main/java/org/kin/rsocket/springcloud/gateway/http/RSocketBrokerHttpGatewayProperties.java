package org.kin.rsocket.springcloud.gateway.http;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author huangjianqin
 * @date 2021/4/20
 */
@ConfigurationProperties(prefix = "kin.rsocket.gateway")
public class RSocketBrokerHttpGatewayProperties {
    /** http gateway是否开启api校验 */
    private boolean restApiAuth = false;

    //setter && getter
    public boolean isRestApiAuth() {
        return restApiAuth;
    }

    public void setRestApiAuth(boolean restApiAuth) {
        this.restApiAuth = restApiAuth;
    }
}
