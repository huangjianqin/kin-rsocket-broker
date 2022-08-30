package org.kin.rsocket.broker;

import org.kin.rsocket.auth.AuthenticationService;
import org.kin.rsocket.auth.JwtAuthenticationService;
import org.kin.rsocket.auth.NoneAuthenticationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * @author huangjianqin
 * @date 2021/4/1
 */
//权限校验需在broker auto configuration前加载
@AutoConfigureBefore(RSocketBrokerAutoConfiguration.class)
@EnableConfigurationProperties(RSocketBrokerProperties.class)
public class RSocketBrokerJwtAuthAutoConfiguration {
    private static final String ISSUER = "KinRSocketBroker";

    @Bean
    public AuthenticationService authenticationService(@Autowired RSocketBrokerProperties brokerConfig) throws Exception {
        if (brokerConfig.isAuth()) {
            return new JwtAuthenticationService(ISSUER, brokerConfig.getAuthDir());
        } else {
            return NoneAuthenticationService.INSTANCE;
        }
    }
}
