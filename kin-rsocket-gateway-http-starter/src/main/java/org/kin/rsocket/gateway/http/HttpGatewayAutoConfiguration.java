package org.kin.rsocket.gateway.http;

import org.kin.rsocket.gateway.http.converter.ByteBufDecoder;
import org.kin.rsocket.gateway.http.converter.ByteBufEncoder;
import org.kin.rsocket.service.boot.RSocketServiceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
@EnableConfigurationProperties({RSocketBrokerHttpGatewayProperties.class, RSocketServiceProperties.class})
public class HttpGatewayAutoConfiguration implements WebFluxConfigurer {
    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        configurer.customCodecs().register(new EncoderHttpMessageWriter<>(new ByteBufEncoder()));
        configurer.customCodecs().register(new DecoderHttpMessageReader<>(new ByteBufDecoder()));
    }

    //-------------------------------controller-------------------------------
    @Bean
    public HttpGatewayController httpGatewayController() {
        return new HttpGatewayController();
    }
}
