package org.kin.spring.rsocket.support;

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
@ConditionalOnBean({SpringRSocketServiceDiscoveryMarkerConfiguration.Marker.class, ReactiveDiscoveryClient.class})
public class SpringRSocketServiceDiscoveryAutoConfiguration {
    /**
     * 基于{@link ReactiveDiscoveryClient}发现naming service注册的rsocket service实例并缓存其元数据, 用于创建支持load balance rsocket requester
     */
    @Bean
    public SpringRSocketServiceDiscoveryRegistry springRSocketServiceDiscoveryRegistry(ReactiveDiscoveryClient discoveryClient) {
        return new SpringRSocketServiceDiscoveryRegistry(discoveryClient);
    }

    /**
     * {@link SpringRSocketServiceDiscoveryRegistry}rsocket service实例元数据上报, 并通过actuator查看
     */
    @Bean
    public SpringRSocketServiceDiscoveryRegistryEndpoint springRSocketServiceDiscoveryRegistryEndpoint(SpringRSocketServiceDiscoveryRegistry registry) {
        return new SpringRSocketServiceDiscoveryRegistryEndpoint(registry);
    }

    @Bean
    @ConditionalOnMissingBean
    public LoadbalanceStrategyFactory defaultLoadbalanceStrategyFactory() {
        return LoadbalanceStrategyFactory.WEIGHTED;
    }
}
