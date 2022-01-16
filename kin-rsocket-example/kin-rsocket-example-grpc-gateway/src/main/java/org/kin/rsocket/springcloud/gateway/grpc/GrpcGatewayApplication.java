package org.kin.rsocket.springcloud.gateway.grpc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * @author huangjianqin
 * @date 2022/1/16
 */
@SpringBootApplication
@EnableRSocketGrpcServiceReference(basePackages = "org.kin.rsocket.example")
public class GrpcGatewayApplication implements WebFluxConfigurer {
    public static void main(String[] args) {
        SpringApplication.run(GrpcGatewayApplication.class, args);
    }
}
