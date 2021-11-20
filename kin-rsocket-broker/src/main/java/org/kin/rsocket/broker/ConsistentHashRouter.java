package org.kin.rsocket.broker;

import io.netty.buffer.ByteBuf;
import org.kin.framework.collection.ConcurrentHashSet;
import org.kin.rsocket.core.ServiceLocator;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一致性hash路由算法
 *
 * @author huangjianqin
 * @date 2021/11/20
 */
public class ConsistentHashRouter implements ProviderRouter {
    /** hash环每个节点数量(含虚拟节点) */
    private static final int HASH_NODE_NUM = 64;

    /** key -> serviceId, value -> 一致性hash环 */
    private final ConcurrentHashMap<Integer, ConsistentHash> serviceId2Hash = new ConcurrentHashMap<>();

    @Override
    public Integer route(int serviceId, ByteBuf paramBytes) {
        ConsistentHash consistentHash = serviceId2Hash.get(serviceId);
        if (Objects.isNull(consistentHash)) {
            return null;
        }

        //以请求消息作为hash参数
        byte[] bytes = new byte[paramBytes.readableBytes()];
        paramBytes.markReaderIndex();
        paramBytes.readBytes(bytes);
        Integer target = consistentHash.get(bytes);
        paramBytes.resetReaderIndex();
        return target;
    }

    @Override
    public void onAppRegistered(int instanceId, int weight, Collection<ServiceLocator> services) {
        for (ServiceLocator serviceLocator : services) {
            int serviceId = serviceLocator.getId();

            ConsistentHash consistentHash = serviceId2Hash.get(serviceId);
            if (Objects.isNull(consistentHash)) {
                consistentHash = new ConsistentHash();
                serviceId2Hash.put(serviceId, consistentHash);
            }
            weight = Math.max(weight, 1);
            consistentHash.add(instanceId, weight);
        }
    }

    @Override
    public void onServiceUnregistered(int instanceId, int weight, Collection<Integer> serviceIds) {
        for (Integer serviceId : serviceIds) {
            ConsistentHash consistentHash = serviceId2Hash.get(serviceId);
            if (Objects.isNull(consistentHash)) {
                continue;
            }

            consistentHash.remove(instanceId, weight);
        }
    }

    @Override
    public Collection<Integer> getAllInstanceIds(int serviceId) {
        ConsistentHash consistentHash = serviceId2Hash.get(serviceId);
        if (Objects.isNull(consistentHash)) {
            return Collections.emptyList();
        }
        return consistentHash.instanceIds;
    }

    private static class ConsistentHash extends org.kin.framework.utils.ConsistentHash<Integer> {
        /** 缓存所有app instance Id */
        private final ConcurrentHashSet<Integer> instanceIds = new ConcurrentHashSet<>();

        public ConsistentHash() {
            super(HASH_NODE_NUM);
        }

        @Override
        public void add(Integer obj, int weight) {
            instanceIds.add(obj);
            super.add(obj, weight);
        }

        @Override
        public void remove(Integer obj, int weight) {
            instanceIds.remove(obj);
            super.remove(obj, weight);
        }
    }
}
