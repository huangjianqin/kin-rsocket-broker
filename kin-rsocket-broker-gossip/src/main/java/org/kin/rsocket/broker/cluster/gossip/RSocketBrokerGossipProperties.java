package org.kin.rsocket.broker.cluster.gossip;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author huangjianqin
 * @date 2021/3/29
 */
@ConfigurationProperties(prefix = "kin.rsocket.broker.gossip")
public class RSocketBrokerGossipProperties {
    /** Gossip listen port */
    private int gossipPort;

    //setter && getter
    public int getGossipPort() {
        return gossipPort;
    }

    public void setGossipPort(int gossipPort) {
        this.gossipPort = gossipPort;
    }
}
