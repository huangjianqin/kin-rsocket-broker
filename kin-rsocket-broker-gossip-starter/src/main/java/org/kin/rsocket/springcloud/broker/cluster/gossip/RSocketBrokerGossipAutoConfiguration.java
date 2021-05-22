package org.kin.rsocket.springcloud.broker.cluster.gossip;

import org.kin.rsocket.broker.RSocketBrokerProperties;
import org.kin.rsocket.broker.cluster.BrokerManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * @author huangjianqin
 * @date 2021/3/29
 */
@Order(100)
@Configuration
@EnableConfigurationProperties(RSocketBrokerGossipProperties.class)
public class RSocketBrokerGossipAutoConfiguration {
    @Bean
    @ConditionalOnBean(RSocketBrokerProperties.class)
    @ConditionalOnProperty("kin.rsocket.broker.gossip.port")
    public BrokerManager brokerManager(@Autowired RSocketBrokerProperties brokerConfig,
                                       @Autowired RSocketBrokerGossipProperties gossipConfig) {
        return new GossipBrokerManager(brokerConfig, gossipConfig);
    }
}
