package org.kin.rsocket.conf.memory;

import org.kin.rsocket.conf.ConfDiamond;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author huangjianqin
 * @date 2021/4/2
 */
@Configuration
public class RsocketMemoryStorageConfAutoConfiguration {
    /**
     * 默认基于内存的配置中心实现
     */
    @Bean
    @ConditionalOnMissingBean
    public ConfDiamond configurationService() {
        return new MemoryStorageConfDiamond();
    }
}
