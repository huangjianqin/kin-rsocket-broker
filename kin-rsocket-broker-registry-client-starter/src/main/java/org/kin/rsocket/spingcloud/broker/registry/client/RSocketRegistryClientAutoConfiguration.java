package org.kin.rsocket.spingcloud.broker.registry.client;

import org.kin.rsocket.core.discovery.DiscoveryService;
import org.kin.rsocket.service.ServiceReferenceBuilder;
import org.kin.rsocket.service.UpstreamClusterManager;
import org.springframework.beans.factory.annotation.Autowired;
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
public class RSocketRegistryClientAutoConfiguration {
    @Bean(autowireCandidate = false)
    public DiscoveryService discoveryService(@Autowired UpstreamClusterManager upstreamClusterManager) {
        return ServiceReferenceBuilder
                .requester(DiscoveryService.class)
                .upstreamClusterManager(upstreamClusterManager)
                .build();
    }

    @Bean
    public ReactiveDiscoveryClient discoveryClient() {
        return new RSocketDiscoveryClient(discoveryService(null));
    }
}
