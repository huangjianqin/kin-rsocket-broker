package org.kin.rsocket.springcloud.gateway.grpc;

import io.grpc.health.v1.HealthGrpc;
import org.kin.rsocket.springcloud.gateway.grpc.health.HealthServiceImpl;
import org.lognet.springboot.grpc.autoconfigure.GRpcAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author huangjianqin
 * @date 2022/1/9
 */
@Configuration
@AutoConfigureBefore(GRpcAutoConfiguration.class)
public class GrpcGatewayAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(HealthGrpc.HealthImplBase.class)
    public HealthServiceImpl grpcHealthService() {
        return new HealthServiceImpl();
    }
}
