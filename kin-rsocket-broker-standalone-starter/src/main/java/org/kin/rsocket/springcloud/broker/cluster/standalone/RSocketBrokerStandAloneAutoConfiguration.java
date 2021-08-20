package org.kin.rsocket.springcloud.broker.cluster.standalone;

import org.kin.rsocket.broker.RSocketBrokerAutoConfiguration;
import org.kin.rsocket.broker.cluster.BrokerManager;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author huangjianqin
 * @date 2021/5/22
 */
@Configuration
@AutoConfigureAfter(RSocketBrokerAutoConfiguration.class)
public class RSocketBrokerStandAloneAutoConfiguration {
    /**
     * 默认{@link BrokerManager}实现, 可通过maven依赖配置其他starter来使用自定义{@link BrokerManager}实现
     */
    @Bean
    @ConditionalOnMissingBean(BrokerManager.class)
    public BrokerManager brokerManager() {
        return new StandAloneBrokerManager();
    }
}
