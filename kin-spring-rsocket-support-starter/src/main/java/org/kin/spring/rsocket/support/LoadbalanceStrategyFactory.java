package org.kin.spring.rsocket.support;

import io.rsocket.loadbalance.ClientLoadbalanceStrategy;
import io.rsocket.loadbalance.LoadbalanceStrategy;
import io.rsocket.loadbalance.RoundRobinLoadbalanceStrategy;
import io.rsocket.loadbalance.WeightedLoadbalanceStrategy;

/**
 * 创建{@link ClientLoadbalanceStrategy}实例工厂
 *
 * @author huangjianqin
 * @date 2022/3/18
 */
@FunctionalInterface
public interface LoadbalanceStrategyFactory {
    /** rsocket自带round robin实现 */
    LoadbalanceStrategyFactory ROUND_ROBIN = RoundRobinLoadbalanceStrategy::new;
    /** rsocket自带基于链路响应时间计算权重加权随机实现 */
    LoadbalanceStrategyFactory WEIGHTED = WeightedLoadbalanceStrategy::create;


    /** 创建{@link ClientLoadbalanceStrategy}实例 */
    LoadbalanceStrategy strategy();
}
