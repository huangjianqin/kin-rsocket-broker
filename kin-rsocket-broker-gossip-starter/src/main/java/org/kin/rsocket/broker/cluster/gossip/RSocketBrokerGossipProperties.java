package org.kin.rsocket.broker.cluster.gossip;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author huangjianqin
 * @date 2021/3/29
 */
@ConfigurationProperties(prefix = "kin.rsocket.broker.gossip")
public class RSocketBrokerGossipProperties {
    /** Gossip listen port */
    private int port = 10999;
    /** gossip node, host:port */
    private String[] seeds;
    /** gossip集群命名空间 需要一致才能通信, 默认 */
    private String namespace = "RSocketBroker";

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

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
}
