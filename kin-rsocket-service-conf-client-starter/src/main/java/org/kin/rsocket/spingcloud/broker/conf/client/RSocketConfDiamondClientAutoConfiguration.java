package org.kin.rsocket.spingcloud.broker.conf.client;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RSocket cloud config auto configuration
 *
 * @author huangjianqin
 * @date 2021/4/20
 */
@Configuration
@EnableConfigurationProperties(RSocketServiceConfProperties.class)
public class RSocketConfDiamondClientAutoConfiguration {
    @Bean
    public ConfigChangedEventConsumer configChangedEventConsumer() {
        return new ConfigChangedEventConsumer();
    }
}
