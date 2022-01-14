package org.kin.rsocket.springcloud.gateway.grpc;

import io.rsocket.RSocket;
import org.kin.rsocket.service.UpstreamClusterManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author huangjianqin
 * @date 2022/1/9
 */
@Configuration
public class GrpcGatewayAutoConfiguration {
    @Bean
    public RSocket brokerRequester(UpstreamClusterManager upstreamClusterManager) {
        return upstreamClusterManager.getBroker().getLoadBalanceRequester();
    }
}
