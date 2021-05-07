package org.kin.rsocket.broker;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import org.kin.framework.utils.CollectionUtils;
import org.kin.rsocket.core.ServiceLocator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 加权随机路由
 * 当全部app权重都一样时, 即为简单Random
 *
 * @author huangjianqin
 * @date 2021/5/7
 */
public class WeightedRandomRouter implements Router {
    /** key -> serviceId, value -> list(instanceId, 也就是hash(app uuid) (会重复的, 数量=权重, 用于随机获取对应的instanceId)) */
    private final ListMultimap<Integer, Integer> serviceId2InstanceIds = MultimapBuilder.hashKeys().arrayListValues().build();

    @Override
    public Integer route(Integer serviceId) {
        int instanceId;
        List<Integer> instanceIds = serviceId2InstanceIds.get(serviceId);
        int handlerCount = instanceIds.size();
        if (handlerCount > 1) {
            try {
                instanceId = instanceIds.get(ThreadLocalRandom.current().nextInt(handlerCount));
            } catch (Exception e) {
                instanceId = instanceIds.get(0);
            }
        } else if (handlerCount == 1) {
            instanceId = instanceIds.get(0);
        } else {
            return null;
        }

        return instanceId;
    }

    @Override
    public void onAppRegistered(Integer instanceId, int weight, Collection<ServiceLocator> services) {
        for (ServiceLocator serviceLocator : services) {
            int serviceId = serviceLocator.getId();

            for (int i = 0; i < weight; i++) {
                //put n个
                serviceId2InstanceIds.put(serviceId, instanceId);
            }
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public void onServiceUnregistered(Integer instanceId, Collection<Integer> serviceIds) {
        for (Integer serviceId : serviceIds) {
            //移除所有相同的instanceId
            while (serviceId2InstanceIds.remove(serviceId, instanceId)) {
                //do nothing
            }
        }
    }

    @Override
    public Collection<Integer> getAllInstanceIds(Integer serviceId) {
        List<Integer> routerWeights = serviceId2InstanceIds.get(serviceId);
        if (CollectionUtils.isNonEmpty(routerWeights)) {
            return new ArrayList<>(serviceId2InstanceIds.get(serviceId));
        }

        return Collections.emptyList();
    }
}
