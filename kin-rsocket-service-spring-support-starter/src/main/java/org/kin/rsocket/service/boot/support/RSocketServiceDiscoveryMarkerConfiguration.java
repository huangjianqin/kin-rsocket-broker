package org.kin.rsocket.service.boot.support;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * marker, 用于让{@link RSocketServiceDiscoveryAutoConfiguration}生效
 *
 * @author huangjianqin
 * @date 2022/3/18
 * @see RSocketServiceDiscoveryAutoConfiguration
 */
@Configuration(proxyBeanMethods = false)
public class RSocketServiceDiscoveryMarkerConfiguration {
    public RSocketServiceDiscoveryMarkerConfiguration() {
    }

    @Bean
    public RSocketServiceDiscoveryMarkerConfiguration.Marker rsocketServiceDiscoveryMarkerBean() {
        return new RSocketServiceDiscoveryMarkerConfiguration.Marker();
    }

    class Marker {
        Marker() {
        }
    }
}
