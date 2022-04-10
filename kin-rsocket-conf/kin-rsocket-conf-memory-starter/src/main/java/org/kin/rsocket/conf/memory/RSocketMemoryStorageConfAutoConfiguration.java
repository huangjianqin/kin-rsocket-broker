package org.kin.rsocket.conf.memory;

import org.kin.rsocket.conf.ConfDiamond;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author huangjianqin
 * @date 2021/4/2
 */
@Configuration
public class RSocketMemoryStorageConfAutoConfiguration {
    /**
     * 默认基于内存的配置中心实现
     */
    @Bean
    public ConfDiamond memoryStorageConfDiamond() {
        return new MemoryStorageConfDiamond();
    }
}
