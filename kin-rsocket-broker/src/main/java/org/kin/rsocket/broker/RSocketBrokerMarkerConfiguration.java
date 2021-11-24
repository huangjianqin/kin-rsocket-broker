package org.kin.rsocket.broker;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注入{@link Marker}bean, 进而触发加载{@link RSocketBrokerAutoConfiguration}
 *
 * @author huangjianqin
 * @date 2021/8/20
 */
@Configuration(proxyBeanMethods = false)
public class RSocketBrokerMarkerConfiguration {
    @Bean
    public RSocketBrokerMarkerConfiguration.Marker rsocketBrokerMarker() {
        return new RSocketBrokerMarkerConfiguration.Marker();
    }

    static class Marker {
        Marker() {
        }
    }
}
