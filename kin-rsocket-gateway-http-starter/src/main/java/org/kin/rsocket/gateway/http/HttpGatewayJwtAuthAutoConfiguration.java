package org.kin.rsocket.gateway.http;

import org.kin.rsocket.auth.AuthenticationService;
import org.kin.rsocket.auth.JwtAuthenticationService;
import org.kin.rsocket.auth.NoneAuthenticationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * @author huangjianqin
 * @date 2021/4/1
 */
@EnableConfigurationProperties(RSocketBrokerHttpGatewayProperties.class)
public class HttpGatewayJwtAuthAutoConfiguration {
    private static final String ISSUER = "KinRSocketHttpGateway";

    @Bean
    public AuthenticationService authenticationService(@Autowired RSocketBrokerHttpGatewayProperties config) throws Exception {
        if (config.isRestApiAuth()) {
            return new JwtAuthenticationService(ISSUER, config.getAuthDir());
        } else {
            return NoneAuthenticationService.INSTANCE;
        }
    }
}
