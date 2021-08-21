package org.kin.rsocket.auth.jwt;

import org.kin.rsocket.auth.AuthenticationService;
import org.kin.rsocket.auth.RSocketAuthConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.io.File;

/**
 * @author huangjianqin
 * @date 2021/4/1
 */
@RSocketAuthConfiguration
public class RSocketJwtAuthAutoConfiguration {
    /**
     * rsocket broker
     */
    @Bean("authenticationService")
    @ConditionalOnProperty(name = "kin.rsocket.broker.auth")
    @ConditionalOnMissingBean
    public AuthenticationService brokerAuthenticationService(@Value("${kin.rsocket.broker.authDir}") String authDir) throws Exception {
        return new JwtAuthenticationService(true, new File(authDir));
    }


    /**
     * rsocket service
     */
    @Bean("authenticationService")
    @ConditionalOnMissingBean
    public AuthenticationService authenticationService() throws Exception {
        return new JwtAuthenticationService();
    }
}
