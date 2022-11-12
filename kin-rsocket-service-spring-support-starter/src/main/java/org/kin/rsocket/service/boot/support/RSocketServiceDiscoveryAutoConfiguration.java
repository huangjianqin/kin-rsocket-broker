package org.kin.rsocket.service.boot.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author huangjianqin
 * @date 2022/3/15
 */
@Configuration
@EnableScheduling
@ConditionalOnBean({RSocketServiceDiscoveryMarkerConfiguration.Marker.class, ReactiveDiscoveryClient.class})
public class RSocketServiceDiscoveryAutoConfiguration {
    /**
     * 基于{@link ReactiveDiscoveryClient}发现naming service注册的rsocket service实例并缓存其元数据, 用于创建支持load balance rsocket requester
     */
    @Bean
    public RSocketServiceDiscoveryRegistry rsocketServiceDiscoveryRegistry(ReactiveDiscoveryClient discoveryClient,
                                                                           @Autowired(required = false) RSocketRequesterBuilderCustomizer customizer) {
        return new RSocketServiceDiscoveryRegistry(discoveryClient, customizer);
    }

    /**
     * {@link RSocketServiceDiscoveryRegistry}rsocket service实例元数据上报, 并通过actuator查看
     */
    @Bean
    public RSocketServiceDiscoveryRegistryEndpoint rsocketServiceDiscoveryRegistryEndpoint(RSocketServiceDiscoveryRegistry registry) {
        return new RSocketServiceDiscoveryRegistryEndpoint(registry);
    }

    @Bean
    @ConditionalOnMissingBean
    public LoadbalanceStrategyFactory defaultLoadbalanceStrategyFactory() {
        return LoadbalanceStrategyFactory.WEIGHTED;
    }
}
