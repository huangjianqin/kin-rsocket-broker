package org.kin.rsocket.conf.server;

import org.kin.rsocket.conf.ConfDiamond;
import org.kin.rsocket.conf.server.controller.ConfigController;
import org.kin.rsocket.core.conf.ConfigurationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author huangjianqin
 * @date 2022/4/9
 */
@Configuration
@ConditionalOnBean({ConfDiamond.class, RSocketConfServerMarkerConfiguration.Marker.class})
public class RSocketConfServerAutoConfiguration {
    @Bean
    public ConfigController configController() {
        return new ConfigController();
    }

    @Bean
    public ConfigurationService configurationService() {
        return new ConfigurationServiceImpl();
    }
}
