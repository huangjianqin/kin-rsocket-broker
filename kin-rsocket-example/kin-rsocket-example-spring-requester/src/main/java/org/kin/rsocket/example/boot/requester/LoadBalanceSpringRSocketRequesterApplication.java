package org.kin.rsocket.example.boot.requester;

import org.kin.rsocket.example.boot.UserService;
import org.kin.rsocket.service.boot.support.EnableLBRSocketServiceReference;
import org.kin.rsocket.service.boot.support.RSocketServiceReference;
import org.kin.rsocket.service.boot.support.RSocketServiceReferenceFactoryBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.core.codec.ByteBufferEncoder;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.messaging.rsocket.RSocketStrategies;

/**
 * 整合spring cloud discovery样例
 *
 * @author huangjianqin
 * @date 2021/8/23
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableLBRSocketServiceReference
public class LoadBalanceSpringRSocketRequesterApplication {
    public static void main(String[] args) {
        SpringApplication.run(LoadBalanceSpringRSocketRequesterApplication.class, args);
    }

    @Bean
    @RSocketServiceReference(interfaceClass = UserService.class, appName = "org-kin-spring-rsocket-example")
    public RSocketServiceReferenceFactoryBean<UserService> userService() {
        return new RSocketServiceReferenceFactoryBean<>();
    }

    @Bean
    public RSocketStrategies rsocketStrategies() {
        return RSocketStrategies.builder()
                .encoders(encoders -> {
                    encoders.add(new Jackson2JsonEncoder());
                    encoders.add(new ByteBufferEncoder());
                })
                .decoders(decoders -> decoders.add(new Jackson2JsonDecoder()))
                .build();
    }
}
