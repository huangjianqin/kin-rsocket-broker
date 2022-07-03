package org.kin.rsocket.example.springcloud.gateway.grpc;

import org.kin.rsocket.springcloud.gateway.grpc.EnableGrpcServiceRSocketImplementation;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * @author huangjianqin
 * @date 2022/1/16
 */
@SpringBootApplication
@EnableGrpcServiceRSocketImplementation(basePackages = "org.kin.rsocket.example")
public class GrpcGatewayApplication implements WebFluxConfigurer {
    public static void main(String[] args) {
        SpringApplication.run(GrpcGatewayApplication.class, args);
    }

//    @Bean
//    public GrpcServiceRSocketImplementationFactoryBean<ReactorUserServiceGrpc.UserServiceImplBase> userService(){
//        return new GrpcServiceRSocketImplementationFactoryBean<>(ReactorUserServiceGrpc.UserServiceImplBase.class);
//    }
}
