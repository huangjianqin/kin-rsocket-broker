package org.kin.rsocket.spingcloud.conf.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RSocket cloud config auto configuration
 *
 * @author leijuan
 */
@Configuration
public class RSocketConfDiamondClientAutoConfiguration {
    @Bean
    public ConfigChangedEventConsumer configChangedEventConsumer() {
        return new ConfigChangedEventConsumer();
    }
}
