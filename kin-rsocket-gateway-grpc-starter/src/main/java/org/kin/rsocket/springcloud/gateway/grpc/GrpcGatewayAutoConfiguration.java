package org.kin.rsocket.springcloud.gateway.grpc;

import io.grpc.health.v1.HealthGrpc;
import org.kin.rsocket.springcloud.gateway.grpc.health.HealthServiceImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author huangjianqin
 * @date 2022/1/9
 */
@Configuration
public class GrpcGatewayAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(HealthGrpc.HealthImplBase.class)
    public HealthGrpc.HealthImplBase grpcHealthService() {
        return new HealthServiceImpl();
    }
}
