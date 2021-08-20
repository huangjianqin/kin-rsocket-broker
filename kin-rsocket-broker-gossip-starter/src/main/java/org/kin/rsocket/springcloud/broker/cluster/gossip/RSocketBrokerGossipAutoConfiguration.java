package org.kin.rsocket.springcloud.broker.cluster.gossip;

import org.kin.rsocket.broker.RSocketBrokerAutoConfiguration;
import org.kin.rsocket.broker.RSocketBrokerProperties;
import org.kin.rsocket.broker.cluster.RSocketBrokerManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
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
@ConditionalOnBean(RSocketBrokerProperties.class)
@ConditionalOnProperty(prefix = "kin.rsocket.broker.gossip", name = {"port", "seeds[0]"})
@Configuration
@EnableConfigurationProperties(RSocketBrokerGossipProperties.class)
@AutoConfigureAfter(RSocketBrokerAutoConfiguration.class)
public class RSocketBrokerGossipAutoConfiguration {
    /**
     * 配置了gossip cluster绑定端口, 并且至少有一个seed, 也就是member, 才有效启动gossip 集群模式
     */
    @Bean
    public RSocketBrokerManager brokerManager(@Autowired RSocketBrokerProperties brokerConfig,
                                              @Autowired RSocketBrokerGossipProperties gossipConfig) {
        return new GossipBrokerManager(brokerConfig, gossipConfig);
    }
}
