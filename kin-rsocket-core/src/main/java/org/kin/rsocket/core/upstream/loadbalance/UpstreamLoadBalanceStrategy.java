package org.kin.rsocket.core.upstream.loadbalance;

/**
 * 上游服务(broker|p2p service)负载均衡策略
 *
 * @author huangjianqin
 * @date 2021/11/23
 */
public enum UpstreamLoadBalanceStrategy {
    /** 轮询 */
    ROUND_ROBIN("roundRobin"),
    /** 随机 */
    RANDOM("random"),
    /** 一致性hash */
    CONSISTENT_HASH("consistentHash"),
    /** 基于请求响应时间计算权重 */
    WEIGHTED_STATS("weightedStats"),
    ;

    private final String name;

    UpstreamLoadBalanceStrategy(String name) {
        this.name = name;
    }

    //getter
    public String getName() {
        return name;
    }
}
