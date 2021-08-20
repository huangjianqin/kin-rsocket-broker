package org.kin.rsocket.conf.redis;

import org.kin.rsocket.conf.ConfDiamond;
import org.kin.rsocket.conf.RSocketConfDiamondConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * @author huangjianqin
 * @date 2021/8/20
 */
@RSocketConfDiamondConfiguration
public class RSocketRedisStorageConfAutoConfiguration {
    @Bean
    @ConditionalOnProperty("spring.redis.port")
    public ConfDiamond configurationService() {
        return new RedisStorageConfDiamond();
    }
}
