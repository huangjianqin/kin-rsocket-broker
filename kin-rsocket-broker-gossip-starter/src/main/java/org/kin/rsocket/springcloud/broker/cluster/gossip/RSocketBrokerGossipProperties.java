package org.kin.rsocket.springcloud.broker.cluster.gossip;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author huangjianqin
 * @date 2021/3/29
 */
@ConfigurationProperties(prefix = "kin.rsocket.broker.gossip")
public class RSocketBrokerGossipProperties {
    /** Gossip listen port */
    private int port = 10999;
    /** gossip node */
    private String[] seeds;

    //setter && getter
    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String[] getSeeds() {
        return seeds;
    }

    public void setSeeds(String[] seeds) {
        this.seeds = seeds;
    }
}
