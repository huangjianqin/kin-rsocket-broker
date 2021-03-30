package org.kin.rsocket.broker.cluster.gossip;

import org.kin.rsocket.broker.cluster.RSocketBrokerManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * @author huangjianqin
 * @date 2021/3/29
 */
@Configuration
@EnableConfigurationProperties(RSocketBrokerGossipProperties.class)
public class RSocketBrokerGossipAutoConfiguration {
    @Bean
    @ConditionalOnExpression("'${rsocket.broker.topology}'=='gossip'")
    @Primary
    public RSocketBrokerManager GossipBrokerManager() {
        return new GossipBrokerManager();
    }
}
