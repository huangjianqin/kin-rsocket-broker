package org.kin.rsocket.springcloud.gateway.http;

import io.rsocket.RSocket;
import org.kin.rsocket.auth.AuthenticationService;
import org.kin.rsocket.core.UpstreamCluster;
import org.kin.rsocket.service.RSocketRequesterSupportImpl;
import org.kin.rsocket.service.UpstreamClusterManager;
import org.kin.rsocket.springcloud.gateway.http.converter.ByteBufDecoder;
import org.kin.rsocket.springcloud.gateway.http.converter.ByteBufEncoder;
import org.kin.rsocket.springcloud.service.RSocketServiceProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.DecoderHttpMessageReader;
import org.springframework.http.codec.EncoderHttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

import java.util.Objects;

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
    public HttpGatewayController httpGatewayController(@Autowired(required = false) UpstreamClusterManager upstreamClusterManager,
                                                       @Autowired AuthenticationService authenticationService,
                                                       @Autowired RSocketBrokerHttpGatewayProperties httpGatewayConfig,
                                                       @Autowired RSocketServiceProperties serviceConfig,
                                                       @Value("${spring.application.name:rsocket-http-gateway}") String appName) {
        RSocket brokerRSocket;
        if (Objects.nonNull(upstreamClusterManager)) {
            //配置有UpstreamClusterManager bean
            brokerRSocket = upstreamClusterManager.getBroker();
        } else {
            brokerRSocket = UpstreamCluster.brokerUpstreamCluster(new RSocketRequesterSupportImpl(serviceConfig, appName), serviceConfig.getBrokers());
        }

        if (Objects.isNull(brokerRSocket)) {
            throw new IllegalStateException("can't not find broker upstream cluster");
        }

        return new HttpGatewayController(brokerRSocket, authenticationService, httpGatewayConfig);
    }

}
