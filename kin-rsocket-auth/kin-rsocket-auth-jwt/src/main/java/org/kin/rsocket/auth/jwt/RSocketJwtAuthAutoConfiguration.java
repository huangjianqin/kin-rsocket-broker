package org.kin.rsocket.auth.jwt;

import org.kin.rsocket.auth.AuthenticationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.io.File;

/**
 * @author huangjianqin
 * @date 2021/4/1
 */
@Configuration
public class RSocketJwtAuthAutoConfiguration {
    /**
     * rsocket broker
     */
    @Bean("authenticationService")
    @ConditionalOnProperty(name = "kin.rsocket.broker.auth")
    @ConditionalOnMissingBean
    @Order(100)
    public AuthenticationService authenticationService0(@Value("${kin.rsocket.broker.authDir}") String authDir) throws Exception {
        return new JwtAuthenticationService(true, new File(authDir));
    }

    /**
     * rsocket service
     */
    @Bean("authenticationService")
    @ConditionalOnMissingBean
    @Order(101)
    public AuthenticationService authenticationService1() throws Exception {
        return new JwtAuthenticationService();
    }
}
