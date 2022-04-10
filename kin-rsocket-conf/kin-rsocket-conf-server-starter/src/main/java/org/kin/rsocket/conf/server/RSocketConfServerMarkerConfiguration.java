package org.kin.rsocket.conf.server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注入{@link Marker}bean, 进而触发加载{@link RSocketConfServerAutoConfiguration}
 *
 * @author huangjianqin
 * @date 2022/4/9
 */
@Configuration(proxyBeanMethods = false)
public class RSocketConfServerMarkerConfiguration {
    @Bean
    public RSocketConfServerMarkerConfiguration.Marker rsocketConfServerMarker() {
        return new RSocketConfServerMarkerConfiguration.Marker();
    }

    static class Marker {
        Marker() {
        }
    }
}
