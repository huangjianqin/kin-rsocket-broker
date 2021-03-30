package org.kin.rsocket.broker;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import io.netty.util.collection.IntObjectHashMap;
import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.utils.MurmurHash3;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 服务 <-> 实例 路由表
 *
 * @author huangjianqin
 * @date 2021/3/29
 */
public class ServiceRouteTable {
    /** key -> serviceId, value -> list(instanceId, 也就是hash(app uuid) (会重复的, 数量=权重, 用于随机获取对应的instanceId)) */
    private final ListMultimap<Integer, Integer> serviceId2InstanceIds = MultimapBuilder.hashKeys().arrayListValues().build();
    /** key -> serviceId, value -> service info */
    private final IntObjectHashMap<ServiceLocator> services = new IntObjectHashMap<>();
    /** key -> instanceId, value -> list(serviceId) */
    private final SetMultimap<Integer, Integer> instanceId2ServiceIds = MultimapBuilder.hashKeys().linkedHashSetValues().build();

    /**
     * 注册app instance及其服务
     */
    public void register(Integer instanceId, Set<ServiceLocator> services) {
        register(instanceId, 1, services);
    }

    /**
     * 注册app instance及其服务
     */
    public void register(Integer instanceId, int powerUnit, Set<ServiceLocator> services) {
        //todo notification for global service
        for (ServiceLocator serviceLocator : services) {
            int serviceId = serviceLocator.getId();
            if (!instanceId2ServiceIds.get(instanceId).contains(serviceId)) {
                instanceId2ServiceIds.put(instanceId, serviceId);
                for (int i = 0; i < powerUnit; i++) {
                    //put n个
                    serviceId2InstanceIds.put(serviceId, instanceId);
                }
                this.services.put(serviceId, serviceLocator);
            }
        }
    }

    /**
     * 注销app instance及其服务
     */
    public void unregister(Integer instanceId) {
        if (instanceId2ServiceIds.containsKey(instanceId)) {
            for (Integer serviceId : instanceId2ServiceIds.get(instanceId)) {
                //移除所有相同的instanceId
                while (serviceId2InstanceIds.remove(serviceId, instanceId)) {
                    //do nothing
                }
                if (!serviceId2InstanceIds.containsKey(serviceId)) {
                    //没有该serviceId对应instanceId了
                    services.remove(serviceId);
                }
            }
            //移除该instanceId对应的所有serviceId
            instanceId2ServiceIds.removeAll(instanceId);
        }
    }

    /**
     * 注销app instance及其服务
     */
    public void unregister(Integer instanceId, Integer serviceId) {
        if (instanceId2ServiceIds.containsKey(instanceId)) {
            //移除所有相同的instanceId
            while (serviceId2InstanceIds.remove(serviceId, instanceId)) {
                //do nothing
            }
            if (!serviceId2InstanceIds.containsKey(serviceId)) {
                //没有该serviceId对应instanceId了
                services.remove(serviceId);
            }
            //移除该instanceId上的serviceId
            instanceId2ServiceIds.remove(instanceId, serviceId);
            if (!instanceId2ServiceIds.containsKey(instanceId)) {
                instanceId2ServiceIds.removeAll(instanceId);
            }
        }
    }

    /**
     * 根据instanceId获取其所有serviceId
     */
    public Set<Integer> getServiceIds(Integer instanceId) {
        return instanceId2ServiceIds.get(instanceId);
    }

    /**
     * instanceId是否已注册
     */
    public boolean containsInstanceId(Integer instanceId) {
        return instanceId2ServiceIds.containsKey(instanceId);
    }

    /**
     * serviceId是否已注册
     */
    public boolean containsServiceId(Integer serviceId) {
        return serviceId2InstanceIds.containsKey(serviceId);
    }

    /**
     * 根据serviceId获取其数据, 即{@link ServiceLocator}
     */
    public ServiceLocator getServiceLocator(Integer serviceId) {
        return services.get(serviceId);
    }

    /**
     * 根据serviceId获取对应instanceId
     */
    public Integer getInstanceId(Integer serviceId) {
        List<Integer> instanceIds = serviceId2InstanceIds.get(serviceId);
        int handlerCount = instanceIds.size();
        if (handlerCount > 1) {
            try {
                return instanceIds.get(ThreadLocalRandom.current().nextInt(handlerCount));
            } catch (Exception e) {
                return instanceIds.get(0);
            }
        } else if (handlerCount == 1) {
            return instanceIds.get(0);
        } else {
            return null;
        }
    }

    /**
     * 根据serviceId获取其所有instanceId
     */
    public Collection<Integer> getAllInstanceIds(Integer serviceId) {
        return serviceId2InstanceIds.get(serviceId);

    }

    /**
     * 统计serviceId对应instanceId数量
     */
    public Integer countInstanceIds(Integer serviceId) {
        //有重复
        List<Integer> instanceIds = serviceId2InstanceIds.get(serviceId);
        return new HashSet<>(instanceIds).size();
    }

    /**
     * 统计serviceId对应instanceId数量
     */
    public Integer countInstanceIds(String serviceName) {
        return countInstanceIds(MurmurHash3.hash32(serviceName));
    }

    /**
     * 获取所有服务数据
     */
    public Collection<ServiceLocator> getAllServices() {
        return services.values();
    }

    /**
     * 已注册服务数量
     */
    public int countServices() {
        return services.keySet().size();
    }
}
