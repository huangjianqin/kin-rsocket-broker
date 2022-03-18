package org.kin.spring.rsocket.support;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * marker, 用于让{@link SpringRSocketServiceDiscoveryAutoConfiguration}生效
 *
 * @author huangjianqin
 * @date 2022/3/18
 * @see SpringRSocketServiceDiscoveryAutoConfiguration
 */
@Configuration(proxyBeanMethods = false)
public class SpringRSocketServiceDiscoveryMarkerConfiguration {
    public SpringRSocketServiceDiscoveryMarkerConfiguration() {
    }

    @Bean
    public SpringRSocketServiceDiscoveryMarkerConfiguration.Marker springRSocketServiceDiscoveryMarkerBean() {
        return new SpringRSocketServiceDiscoveryMarkerConfiguration.Marker();
    }

    class Marker {
        Marker() {
        }
    }
}
