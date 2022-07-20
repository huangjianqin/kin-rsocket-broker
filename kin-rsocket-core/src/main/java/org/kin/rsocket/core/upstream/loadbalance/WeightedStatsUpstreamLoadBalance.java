package org.kin.rsocket.core.upstream.loadbalance;

import io.netty.buffer.ByteBuf;
import io.rsocket.RSocket;
import io.rsocket.loadbalance.WeightedLoadbalanceStrategy;
import io.rsocket.loadbalance.WeightedStats;
import org.jctools.maps.NonBlockingHashMap;
import org.kin.framework.utils.Extension;

import java.util.List;

/**
 * 原理: 基于历史rsocket请求的响应时间来预测本次请求的响应时间, 然后根据预测的响应时间给upstream rsocket分配权重, 最后选择最高权重的upstream rsocket
 * 如果延迟足够好, 可能会一直负载均衡到同一service Instance
 *
 * @author huangjianqin
 * @date 2022/1/15
 * @see io.rsocket.loadbalance.WeightedLoadbalanceStrategy
 */
@Extension("weightedstats")
public class WeightedStatsUpstreamLoadBalance implements UpstreamLoadBalance {
    /** 存储每个{@link RSocket} connector的状态信息, 用于计算其权重 */
    private final NonBlockingHashMap<RSocket, WeightedStats> statsMap = new NonBlockingHashMap<>();
    /** {@link WeightedLoadbalanceStrategy}实例 */
    private final WeightedLoadbalanceStrategy strategy = WeightedLoadbalanceStrategy.builder().weightedStatsResolver(statsMap::get).build();

    @Override
    public RSocket select(int serviceId, ByteBuf paramBytes, List<RSocket> rsockets) {
        return strategy.select(rsockets);
    }

    public void put(RSocket rsocket, WeightedStats weightedStats) {
        statsMap.put(rsocket, weightedStats);
    }

    public void remove(RSocket rsocket) {
        statsMap.remove(rsocket);
    }
}
