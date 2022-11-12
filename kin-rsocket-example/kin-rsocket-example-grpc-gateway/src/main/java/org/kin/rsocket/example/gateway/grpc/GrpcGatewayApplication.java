package org.kin.rsocket.example.gateway.grpc;

import org.kin.rsocket.example.ReactorUserServiceGrpc;
import org.kin.rsocket.gateway.grpc.EnableGrpcServiceRSocketImpl;
import org.kin.rsocket.gateway.grpc.GrpcServiceRSocketImplementationFactoryBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * @author huangjianqin
 * @date 2022/1/16
 */
@SpringBootApplication
@EnableGrpcServiceRSocketImpl(basePackages = "org.kin.rsocket.example")
public class GrpcGatewayApplication implements WebFluxConfigurer {
    public static void main(String[] args) {
        SpringApplication.run(GrpcGatewayApplication.class, args);
    }

    @Bean
    public GrpcServiceRSocketImplementationFactoryBean<ReactorUserServiceGrpc.UserServiceImplBase> userService() {
        return new GrpcServiceRSocketImplementationFactoryBean<>(ReactorUserServiceGrpc.UserServiceImplBase.class);
    }
}
