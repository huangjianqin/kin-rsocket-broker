package org.kin.rsocket.conf.memory;

import org.kin.rsocket.conf.ConfDiamond;
import org.kin.rsocket.conf.RSocketConfDiamondConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * @author huangjianqin
 * @date 2021/4/2
 */
@RSocketConfDiamondConfiguration
public class RSocketMemoryStorageConfAutoConfiguration {
    /**
     * 默认基于内存的配置中心实现
     */
    @Bean
    public ConfDiamond configurationService() {
        return new MemoryStorageConfDiamond();
    }
}
