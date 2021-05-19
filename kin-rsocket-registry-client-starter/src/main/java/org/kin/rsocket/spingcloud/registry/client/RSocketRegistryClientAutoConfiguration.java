package org.kin.rsocket.spingcloud.registry.client;

import org.kin.rsocket.core.discovery.DiscoveryService;
import org.kin.rsocket.service.RSocketServiceReferenceBuilder;
import org.kin.rsocket.service.UpstreamClusterManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.cloud.client.ConditionalOnReactiveDiscoveryEnabled;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RSocket registry client auto configuration
 *
 * @author huangjianqin
 * @date 2021/4/20
 */
@Configuration
@ConditionalOnDiscoveryEnabled
@ConditionalOnReactiveDiscoveryEnabled
public class RSocketRegistryClientAutoConfiguration {
    @Bean
    public DiscoveryService discoveryService(@Autowired UpstreamClusterManager upstreamClusterManager) {
        return RSocketServiceReferenceBuilder
                .requester(DiscoveryService.class)
                .upstreamClusterManager(upstreamClusterManager)
                .build();
    }

    @Bean
    public ReactiveDiscoveryClient discoveryClient(@Autowired DiscoveryService discoveryService) {
        return new RSocketReactiveDiscoveryClient(discoveryService);
    }
}
