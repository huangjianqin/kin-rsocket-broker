package org.kin.rsocket.spingcloud.conf.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author huangjianqin
 * @date 2021/5/16
 */
@Configuration
public class RSocketConfDiamondClientBootstrapConfiguration {
    @Bean
    public RSocketConfigPropertySourceLocator rsocketConfigPropertySourceLocator() {
        return new RSocketConfigPropertySourceLocator();
    }
}
