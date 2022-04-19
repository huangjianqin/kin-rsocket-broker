package org.kin.rsocket.springcloud.broker.cluster.standalone;

import org.kin.rsocket.broker.RSocketBrokerAutoConfiguration;
import org.kin.rsocket.broker.RSocketBrokerProperties;
import org.kin.rsocket.broker.cluster.RSocketBrokerManager;
import org.springframework.beans.factory.annotation.Autowired;
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
     * 默认{@link RSocketBrokerManager}实现, 可通过maven依赖配置其他starter来使用自定义{@link RSocketBrokerManager}实现
     */
    @Bean
    @ConditionalOnMissingBean(RSocketBrokerManager.class)
    public RSocketBrokerManager brokerManager(@Autowired RSocketBrokerProperties brokerConfig) {
        return new StandAloneBrokerManager(brokerConfig);
    }
}
