package org.kin.rsocket.broker;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import org.kin.framework.utils.CollectionUtils;
import org.kin.rsocket.core.ServiceLocator;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 加权轮询路由
 * 平滑处理
 * 当全部app权重都一样时, 即为简单RoundRobin
 *
 * @author huangjianqin
 * @date 2021/5/7
 */
public class SmoothWeightedRoundRobinRouter implements Router {
    /** key -> serviceId, value -> list(RouterWeight) */
    private final ListMultimap<Integer, RouterWeight> serviceId2InstanceRouterWeights = MultimapBuilder.hashKeys().arrayListValues().build();

    @Override
    public Integer route(int serviceId) {
        List<RouterWeight> routerWeights = getByServiceId(serviceId);
        if (CollectionUtils.isNonEmpty(routerWeights)) {
            //总权重
            int sumWeight = 0;
            RouterWeight selected = null;
            for (RouterWeight routerWeight : routerWeights) {
                int weight = routerWeight.getWeight();
                sumWeight += weight;
                routerWeight.incr(weight);

                if (selected == null || routerWeight.getCurrentWeight() > selected.getCurrentWeight()) {
                    selected = routerWeight;
                }
            }

            selected.incr(-sumWeight);

            return selected.getInstanceId();
        }
        return null;
    }

    @Override
    public void onAppRegistered(int instanceId, int weight, Collection<ServiceLocator> services) {
        synchronized (serviceId2InstanceRouterWeights) {
            for (ServiceLocator serviceLocator : services) {
                serviceId2InstanceRouterWeights.put(serviceLocator.getId(), new RouterWeight(instanceId, weight));
            }
        }
    }

    @Override
    public void onServiceUnregistered(int instanceId, Collection<Integer> serviceIds) {
        synchronized (serviceId2InstanceRouterWeights) {
            for (Integer serviceId : serviceIds) {
                List<RouterWeight> routerWeights = serviceId2InstanceRouterWeights.get(serviceId);
                if (CollectionUtils.isEmpty(routerWeights)) {
                    continue;
                }
                routerWeights.removeIf(item -> item.getInstanceId() == instanceId);
            }
        }
    }

    private List<RouterWeight> getByServiceId(int serviceId) {
        synchronized (serviceId2InstanceRouterWeights) {
            //不允许修改原始list
            return Collections.unmodifiableList(serviceId2InstanceRouterWeights.get(serviceId));
        }
    }

    @Override
    public Collection<Integer> getAllInstanceIds(int serviceId) {
        return getByServiceId(serviceId).stream().map(RouterWeight::getInstanceId).collect(Collectors.toList());
    }
}
