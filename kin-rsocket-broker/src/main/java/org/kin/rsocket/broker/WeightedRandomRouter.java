package org.kin.rsocket.broker;

import org.eclipse.collections.impl.multimap.list.FastListMultimap;
import org.kin.rsocket.core.ServiceLocator;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 加权随机路由
 * 当全部app权重都一样时, 即为简单Random
 * <p>
 * 基于copy on write更新数据
 *
 * @author huangjianqin
 * @date 2021/5/7
 */
public class WeightedRandomRouter implements ProviderRouter {
    /** key -> serviceId, value -> list(instanceId, 也就是hash(app uuid) (会重复的, 数量=权重, 用于随机获取对应的instanceId)) */
    private FastListMultimap<Integer, Integer> serviceId2InstanceIds = new FastListMultimap<>();

    @Override
    public Integer route(int serviceId) {
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
    public void onAppRegistered(RSocketEndpoint rsocketEndpoint, int weight, Collection<ServiceLocator> services) {
        //copy on write
        int instanceId = rsocketEndpoint.getId();
        FastListMultimap<Integer, Integer> serviceId2InstanceIds = new FastListMultimap<>(this.serviceId2InstanceIds);
        for (ServiceLocator serviceLocator : services) {
            int serviceId = serviceLocator.getId();

            for (int i = 0; i < weight; i++) {
                //put n个
                serviceId2InstanceIds.put(serviceId, instanceId);
            }
        }
        this.serviceId2InstanceIds = serviceId2InstanceIds;
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public void onServiceUnregistered(int instanceId, int weight, Collection<Integer> serviceIds) {
        //copy on write
        FastListMultimap<Integer, Integer> serviceId2InstanceIds = new FastListMultimap<>(this.serviceId2InstanceIds);
        for (Integer serviceId : serviceIds) {
            //移除所有相同的instanceId
            while (serviceId2InstanceIds.remove(serviceId, instanceId)) {
                //do nothing
            }
        }
        this.serviceId2InstanceIds = serviceId2InstanceIds;
    }

    @Override
    public Collection<Integer> getAllInstanceIds(int serviceId) {
        return serviceId2InstanceIds.get(serviceId).distinct().asUnmodifiable();
    }
}
