package org.kin.rsocket.spingcloud.broker.gateway.http;

import org.kin.rsocket.auth.AuthenticationService;
import org.kin.rsocket.service.UpstreamClusterManager;
import org.kin.rsocket.spingcloud.broker.gateway.http.convert.ByteBufDecoder;
import org.kin.rsocket.spingcloud.broker.gateway.http.convert.ByteBufEncoder;
import org.springframework.beans.factory.annotation.Autowired;
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
@EnableConfigurationProperties(RSocketBrokerHttpGatewayProperties.class)
public class HttpGatewayAutoConfiguration implements WebFluxConfigurer {
    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        configurer.customCodecs().register(new EncoderHttpMessageWriter<>(new ByteBufEncoder()));
        configurer.customCodecs().register(new DecoderHttpMessageReader<>(new ByteBufDecoder()));
    }

    //-------------------------------controller-------------------------------
    @Bean
    public HttpGatewayController httpGatewayController(@Autowired UpstreamClusterManager upstreamClusterManager,
                                                       @Autowired AuthenticationService authenticationService,
                                                       @Autowired RSocketBrokerHttpGatewayProperties httpGatewayConfig) {
        return new HttpGatewayController(upstreamClusterManager, authenticationService, httpGatewayConfig);
    }

}
