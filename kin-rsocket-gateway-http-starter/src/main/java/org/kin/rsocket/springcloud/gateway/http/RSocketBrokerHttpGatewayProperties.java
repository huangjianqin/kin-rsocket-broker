package org.kin.rsocket.springcloud.gateway.http;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author huangjianqin
 * @date 2021/4/20
 */
@ConfigurationProperties(prefix = "kin.rsocket.gateway")
public class RSocketBrokerHttpGatewayProperties {
    private boolean restapiAuth = false;

    //setter && getter
    public boolean isRestapiAuth() {
        return restapiAuth;
    }

    public void setRestapiAuth(boolean restapiAuth) {
        this.restapiAuth = restapiAuth;
    }
}
