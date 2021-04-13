package org.kin.rsocket.broker.cluster.gossip;

import org.kin.rsocket.broker.RSocketBrokerProperties;
import org.kin.rsocket.broker.cluster.BrokerManager;
import org.kin.rsocket.core.event.CloudEventData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Sinks;

/**
 * @author huangjianqin
 * @date 2021/3/29
 */
@Configuration
@EnableConfigurationProperties(RSocketBrokerGossipProperties.class)
public class RSocketBrokerGossipAutoConfiguration {
    @Bean
    @ConditionalOnExpression("'${kin.rsocket.broker.topology}'=='gossip'")
    @Primary
    public BrokerManager GossipBrokerManager(@Autowired @Qualifier("cloudEventSink") Sinks.Many<CloudEventData<?>> cloudEventSink,
                                             @Autowired RSocketBrokerProperties brokerConfig,
                                             @Autowired RSocketBrokerGossipProperties gossipConfig) {
        return new GossipBrokerManager(cloudEventSink, brokerConfig, gossipConfig);
    }
}
