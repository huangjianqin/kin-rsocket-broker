package org.kin.rsocket.broker;

import io.netty.buffer.ByteBuf;
import org.eclipse.collections.impl.map.mutable.UnifiedMap;
import org.eclipse.collections.impl.multimap.list.FastListMultimap;
import org.kin.framework.utils.CollectionUtils;
import org.kin.rsocket.core.ServiceLocator;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RoundRobin(轮询)路由
 *
 * @author huangjianqin
 * @date 2021/5/7
 */
public class RoundRobinRouter implements ProviderRouter {
    /** key -> serviceId, value -> list(instanceId, 也就是hash(app uuid) (不会重复的) */
    private FastListMultimap<Integer, Integer> serviceId2InstanceIds = new FastListMultimap<>();
    /** key -> serviceId, value -> counter */
    private Map<Integer, AtomicInteger> serviceId2Counter = new UnifiedMap<>();

    @Override
    public Integer route(int serviceId, ByteBuf paramBytes) {
        List<Integer> instanceIds = new ArrayList<>(getAllInstanceIds(serviceId));
        AtomicInteger counter = serviceId2Counter.get(serviceId);


        if (CollectionUtils.isNonEmpty(instanceIds)) {
            int count = 0;
            if (Objects.nonNull(counter)) {
                count = counter.getAndIncrement();
            }

            return instanceIds.get(count % instanceIds.size());
        }
        return null;
    }

    @Override
    public void onAppRegistered(int instanceId, int weight, Collection<ServiceLocator> services) {
        //copy on write
        FastListMultimap<Integer, Integer> serviceId2InstanceIds = new FastListMultimap<>();
        Map<Integer, AtomicInteger> serviceId2Counter = new UnifiedMap<>();

        for (ServiceLocator serviceLocator : services) {
            Integer serviceId = serviceLocator.getId();
            serviceId2InstanceIds.put(serviceId, instanceId);
            if (!serviceId2Counter.containsKey(serviceId)) {
                serviceId2Counter.put(serviceId, new AtomicInteger());
            }
        }

        this.serviceId2InstanceIds = serviceId2InstanceIds;
        this.serviceId2Counter = serviceId2Counter;
    }

    @Override
    public void onServiceUnregistered(int instanceId, int weight, Collection<Integer> serviceIds) {
        //copy on write
        FastListMultimap<Integer, Integer> serviceId2InstanceIds = new FastListMultimap<>();
        Map<Integer, AtomicInteger> serviceId2Counter = new UnifiedMap<>();

        for (Integer serviceId : serviceIds) {
            if (serviceId2InstanceIds.remove(serviceId, instanceId)) {
                //移除成功
                if (CollectionUtils.isEmpty(serviceId2InstanceIds.get(serviceId))) {
                    serviceId2Counter.remove(serviceId);
                }
            }
        }

        this.serviceId2InstanceIds = serviceId2InstanceIds;
        this.serviceId2Counter = serviceId2Counter;
    }

    @Override
    public Collection<Integer> getAllInstanceIds(int serviceId) {
        return serviceId2InstanceIds.get(serviceId).asUnmodifiable();
    }
}
