package org.kin.rsocket.core;

import org.kin.framework.utils.SPI;

/**
 * requester端
 * 上游服务(broker)负载均衡策略工厂
 *
 * @author huangjianqin
 * @date 2021/8/20
 */
@SPI(key = "loadBalanceStrategy")
@FunctionalInterface
public interface UpstreamRSocketLoadBalanceStrategy {
    /** 随机 */
    UpstreamRSocketLoadBalanceStrategy RANDOM = () -> UpstreamRSocketLoadBalance.RANDOM;
    /** Round-Robin */
    UpstreamRSocketLoadBalanceStrategy ROUND_ROBIN = RoundRobinLoadBalance::new;

    /**
     * @return 自定义实现的上游服务(broker)负载均衡策略
     */
    UpstreamRSocketLoadBalance strategy();
}
