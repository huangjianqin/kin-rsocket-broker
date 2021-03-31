package org.kin.rsocket.spingcloud.gateway.http;

import org.kin.rsocket.auth.AuthenticationService;
import org.kin.rsocket.auth.JwtAuthenticationService;
import org.kin.rsocket.spingcloud.gateway.http.convert.ByteBufDecoder;
import org.kin.rsocket.spingcloud.gateway.http.convert.ByteBufEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.DecoderHttpMessageReader;
import org.springframework.http.codec.EncoderHttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * @author huangjianqin
 * @date 2021/3/31
 */
@Configuration
public class HttpGatewayConfiguration implements WebFluxConfigurer {
    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        configurer.customCodecs().register(new EncoderHttpMessageWriter<>(new ByteBufEncoder()));
        configurer.customCodecs().register(new DecoderHttpMessageReader<>(new ByteBufDecoder()));
    }

    /**
     * 目前只支持jwt
     */
    @Bean
    public AuthenticationService authenticationService() throws Exception {
        return new JwtAuthenticationService();
    }
}
