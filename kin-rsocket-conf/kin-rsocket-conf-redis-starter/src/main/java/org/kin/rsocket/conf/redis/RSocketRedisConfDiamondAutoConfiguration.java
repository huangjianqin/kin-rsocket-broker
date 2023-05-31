package org.kin.rsocket.conf.redis;

import org.kin.rsocket.conf.ConfDiamond;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author huangjianqin
 * @date 2021/8/20
 */
@Configuration
@AutoConfigureAfter(RedisReactiveAutoConfiguration.class)
public class RSocketRedisConfDiamondAutoConfiguration {
    @Bean
    @ConditionalOnProperty("spring.redis.port")
    public ConfDiamond redisConfDiamond() {
        return new RedisConfDiamond();
    }
}
