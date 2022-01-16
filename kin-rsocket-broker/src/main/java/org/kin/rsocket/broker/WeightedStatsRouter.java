package org.kin.rsocket.broker;

import io.netty.buffer.ByteBuf;
import io.rsocket.RSocket;
import io.rsocket.loadbalance.WeightedStats;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.multimap.list.FastListMultimap;
import org.jctools.maps.NonBlockingHashMap;
import org.kin.rsocket.core.ServiceLocator;
import reactor.util.annotation.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 原理: 基于历史rsocket请求的响应时间来预测本次请求的响应时间, 然后根据预测的响应时间给requester分配权重, 最后选择最高权重的requester
 * 如果延迟足够好, 可能会一直路由到同一service Instance
 *
 * @author huangjianqin
 * @date 2022/1/15
 * @see io.rsocket.loadbalance.WeightedLoadbalanceStrategy
 */
public class WeightedStatsRouter implements ProviderRouter {
    private static final double EXP_FACTOR = 4.0;

    /** 存储每个{@link RSocket} requester的状态信息, 用于计算其权重 */
    private final NonBlockingHashMap<RSocket, WeightedStats> statsMap = new NonBlockingHashMap<>();
    /** key -> serviceId, value -> {@link WeightedInstance} list */
    private FastListMultimap<Integer, WeightedInstance> serviceId2WeightedInstances = new FastListMultimap<>();

    private final int maxPairSelectionAttempts;

    public WeightedStatsRouter() {
        this(5);
    }

    public WeightedStatsRouter(int maxPairSelectionAttempts) {
        this.maxPairSelectionAttempts = maxPairSelectionAttempts;
    }

    @Override
    public Integer route(int serviceId, ByteBuf paramBytes) {
        MutableList<WeightedInstance> weightedInstances = serviceId2WeightedInstances.get(serviceId);
        //有效的rsocket service instance
        List<WeightedInstance> vaildInstances = new ArrayList<>();
        //有效的rsocket service instance及对应的WeightedStats
        Map<Integer, WeightedStats> validStatsMap = new HashMap<>();

        for (WeightedInstance weightedInstance : weightedInstances) {
            int instanceId = weightedInstance.instanceId;
            WeightedStats stats = statsMap.get(weightedInstance.requester);
            if (Objects.isNull(stats)) {
                continue;
            }
            vaildInstances.add(weightedInstance);
            validStatsMap.put(instanceId, stats);
        }

        int size = vaildInstances.size();
        switch (size) {
            case 1:
                //只有1个, 直接返回
                return vaildInstances.get(0).instanceId;
            case 2: {
                //只有两个, 选择权重大的
                WeightedInstance fwi = vaildInstances.get(0);
                Integer fii = fwi.instanceId;
                WeightedStats fws = validStatsMap.get(fii);

                WeightedInstance swi = vaildInstances.get(0);
                Integer sii = vaildInstances.get(1).instanceId;
                WeightedStats sws = validStatsMap.get(sii);

                double w1 = algorithmicWeight(fwi.requester, fws);
                double w2 = algorithmicWeight(swi.requester, sws);
                if (w1 < w2) {
                    return sii;
                } else {
                    return fii;
                }
            }
            default: {
                //大于两个
                WeightedInstance fwi = null;
                WeightedStats fws = null;

                WeightedInstance swi = null;
                WeightedStats sws = null;
                //尝试随机取出两个有效的requester
                List<WeightedInstance> validInstancesDuplicate = new ArrayList<>(weightedInstances);
                //加权随机选出一个availability的requester
                while (validInstancesDuplicate.size() > 0 &&
                        (Objects.isNull((fwi = random(validInstancesDuplicate))) || fwi.requester.availability() <= 0.0)) {
                    //do nothing
                }

                if (Objects.nonNull(fwi)) {
                    fws = validStatsMap.get(fwi.instanceId);
                }

                //再次加权随机选出一个availability的requester
                while (validInstancesDuplicate.size() > 0 &&
                        (Objects.isNull((swi = random(validInstancesDuplicate))) || swi.requester.availability() <= 0.0)) {
                    //do nothing
                }

                if (Objects.nonNull(swi)) {
                    sws = validStatsMap.get(swi.instanceId);
                }

                if (fwi != null & swi != null) {
                    //选择权重大
                    double w1 = algorithmicWeight(fwi.requester, fws);
                    double w2 = algorithmicWeight(swi.requester, sws);
                    if (w1 < w2) {
                        return swi.instanceId;
                    } else {
                        return fwi.instanceId;
                    }
                } else if (fwi != null) {
                    return fwi.instanceId;
                } else {
                    return swi.instanceId;
                }
            }
        }
    }

    /**
     * 按权重随机一个rsocket requester Instance
     */
    private WeightedInstance random(List<WeightedInstance> weightedInstances) {
        int sumWeight = weightedInstances.stream().mapToInt(wi -> wi.weight).sum();
        int random = ThreadLocalRandom.current().nextInt(sumWeight + 1);
        int tempWeight = 0;
        Iterator<WeightedInstance> iterator = weightedInstances.iterator();
        while (iterator.hasNext()) {
            WeightedInstance weightedInstance = iterator.next();
            tempWeight += weightedInstance.weight;
            if (tempWeight >= random) {
                iterator.remove();
                return weightedInstance;
            }
        }

        return null;
    }

    /**
     * 算法计算权重
     */
    private static double algorithmicWeight(RSocket rsocket, @Nullable WeightedStats weightedStats) {
        if (weightedStats == null || rsocket.isDisposed() || rsocket.availability() == 0.0) {
            return 0.0;
        }
        //等待中的请求数量
        int pending = weightedStats.pending();
        //预测延迟时间
        double latency = weightedStats.predictedLatency();
        //预测低分位数(0.5)延迟时间
        double low = weightedStats.lowerQuantileLatency();
        //预测高分位数(0.8)延迟时间, ensure higherQuantile > lowerQuantile + .1%
        double high = Math.max(weightedStats.higherQuantileLatency(), low * 1.001);
        //预测带宽
        double bandWidth = Math.max(high - low, 1);

        if (latency < low) {
            latency /= calculateFactor(low, latency, bandWidth);
        } else if (latency > high) {
            latency *= calculateFactor(latency, high, bandWidth);
        }

        return (rsocket.availability() * weightedStats.weightedAvailability()) / (1.0d + latency * (pending + 1));
    }

    /**
     * 计算系数
     */
    private static double calculateFactor(double u, double l, double bandWidth) {
        double alpha = (u - l) / bandWidth;
        return Math.pow(1 + alpha, EXP_FACTOR);
    }

    @Override
    public void onAppRegistered(BrokerResponder responder, int weight, Collection<ServiceLocator> services) {
        //copy on write
        Integer instanceId = responder.getId();
        FastListMultimap<Integer, WeightedInstance> serviceId2WeightedInstances = new FastListMultimap<>(this.serviceId2WeightedInstances);
        for (ServiceLocator serviceLocator : services) {
            int serviceId = serviceLocator.getId();

            serviceId2WeightedInstances.put(serviceId, new WeightedInstance(instanceId, weight, responder.getRequester()));
        }
        this.serviceId2WeightedInstances = serviceId2WeightedInstances;
    }

    @Override
    public void onServiceUnregistered(int instanceId, int weight, Collection<Integer> serviceIds) {
        //copy on write
        FastListMultimap<Integer, WeightedInstance> serviceId2WeightedInstances = new FastListMultimap<>(this.serviceId2WeightedInstances);
        for (Integer serviceId : serviceIds) {
            MutableList<WeightedInstance> weightedInstances = serviceId2WeightedInstances.get(serviceId);
            List<WeightedInstance> newWeightedInstances = new ArrayList<>();

            for (WeightedInstance weightedInstance : weightedInstances) {
                if (weightedInstance.instanceId == instanceId) {
                    continue;
                }

                newWeightedInstances.add(weightedInstance);
            }
            serviceId2WeightedInstances.replaceValues(serviceId, newWeightedInstances);
        }
        this.serviceId2WeightedInstances = serviceId2WeightedInstances;
    }

    @Override
    public Collection<Integer> getAllInstanceIds(int serviceId) {
        return serviceId2WeightedInstances.get(serviceId).collect(wi -> wi.instanceId).asUnmodifiable();
    }

    public void put(RSocket requester, WeightedStats weightedStats) {
        statsMap.put(requester, weightedStats);
    }

    public void remove(RSocket requester) {
        statsMap.remove(requester);
    }

    private static class WeightedInstance {
        /** app instance id */
        private final int instanceId;
        /** app 权重 */
        private final int weight;
        /** broker 2 service requester */
        private final RSocket requester;

        public WeightedInstance(int instanceId, int weight, RSocket requester) {
            this.instanceId = instanceId;
            this.weight = weight;
            this.requester = requester;
        }
    }
}
