package org.kin.rsocket.broker.cluster.springcloud.discovery;

import org.kin.rsocket.broker.RSocketBrokerProperties;
import org.kin.rsocket.broker.cluster.RSocketBrokerManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * @author huangjianqin
 * @date 2021/8/12
 */
@Order(101)
@ConditionalOnBean({RSocketBrokerProperties.class, ReactiveDiscoveryClient.class})
@Configuration
@EnableConfigurationProperties(RSocketBrokerDiscoveryProperties.class)
public class RSocketBrokerDiscoveryAutoConfiguration {
    @Autowired
    private RSocketBrokerDiscoveryProperties discoveryProperties;

    @Bean
    public RSocketBrokerManager brokerManager(@Autowired ReactiveDiscoveryClient discoveryClient) {
        return new DiscoveryBrokerManager(discoveryClient, discoveryProperties.getService(), discoveryProperties.getRefreshInternal());
    }
}
