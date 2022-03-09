package org.kin.rsocket.broker;

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.multimap.list.FastListMultimap;
import org.kin.framework.utils.CollectionUtils;
import org.kin.rsocket.core.ServiceLocator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 加权RoundRobin(轮询)路由
 *
 * @author huangjianqin
 * @date 2021/5/7
 */
public class WeightedRoundRobinRouter implements ProviderRouter {
    /** key -> serviceId, value -> {@link WeightedRoundRobin} list */
    private FastListMultimap<Integer, WeightedRoundRobin> serviceId2WeightedRoundRobins = new FastListMultimap<>();

    @Override
    public Integer route(int serviceId) {
        MutableList<WeightedRoundRobin> weightedRoundRobins = serviceId2WeightedRoundRobins.get(serviceId);
        if (CollectionUtils.isEmpty(weightedRoundRobins)) {
            return null;
        }

        //总权重
        int totalWeight = 0;
        long maxCurrent = Long.MIN_VALUE;
        WeightedRoundRobin selected = null;
        for (WeightedRoundRobin weightedRoundRobin : weightedRoundRobins) {
            int weight = weightedRoundRobin.weight;
            long current = weightedRoundRobin.incrCurrent();
            if (current > maxCurrent) {
                maxCurrent = current;
                selected = weightedRoundRobin;
            }
            totalWeight += weight;
        }

        if (Objects.nonNull(selected)) {
            selected.descCurrent(totalWeight);
            return selected.instanceId;
        }

        return null;
    }

    @Override
    public void onAppRegistered(RSocketEndpoint rsocketEndpoint, int weight, Collection<ServiceLocator> services) {
        //copy on write
        int instanceId = rsocketEndpoint.getId();
        FastListMultimap<Integer, WeightedRoundRobin> serviceId2WeightedRoundRobins = new FastListMultimap<>(this.serviceId2WeightedRoundRobins);

        for (ServiceLocator serviceLocator : services) {
            Integer serviceId = serviceLocator.getId();
            serviceId2WeightedRoundRobins.put(serviceId, new WeightedRoundRobin(instanceId, weight));
        }

        this.serviceId2WeightedRoundRobins = serviceId2WeightedRoundRobins;
    }

    @Override
    public void onServiceUnregistered(int instanceId, int weight, Collection<Integer> serviceIds) {
        //copy on write
        FastListMultimap<Integer, WeightedRoundRobin> serviceId2WeightedRoundRobins = new FastListMultimap<>(this.serviceId2WeightedRoundRobins);

        for (Integer serviceId : serviceIds) {
            MutableList<WeightedRoundRobin> weightedRoundRobins = serviceId2WeightedRoundRobins.get(serviceId);
            List<WeightedRoundRobin> newWeightedRoundRobins = new ArrayList<>();

            for (WeightedRoundRobin weightedRoundRobin : weightedRoundRobins) {
                if (weightedRoundRobin.instanceId == instanceId) {
                    continue;
                }

                newWeightedRoundRobins.add(weightedRoundRobin);
            }

            serviceId2WeightedRoundRobins.replaceValues(serviceId, newWeightedRoundRobins);
        }

        this.serviceId2WeightedRoundRobins = serviceId2WeightedRoundRobins;
    }

    @Override
    public Collection<Integer> getAllInstanceIds(int serviceId) {
        return serviceId2WeightedRoundRobins.get(serviceId).collect(wrr -> wrr.instanceId).asUnmodifiable();
    }

    private static class WeightedRoundRobin {
        /** app instance id */
        private final int instanceId;
        /** app 权重 */
        private final int weight;
        /** 当前权重累计值 */
        private final AtomicLong current = new AtomicLong();

        public WeightedRoundRobin(int instanceId, int weight) {
            this.instanceId = instanceId;
            this.weight = weight;
        }

        public long incrCurrent() {
            return current.addAndGet(weight);
        }

        public void descCurrent(int total) {
            current.addAndGet(-1 * total);
        }
    }
}
