package org.kin.rsocket.broker;

import org.eclipse.collections.impl.map.mutable.UnifiedMap;
import org.kin.rsocket.core.ServiceLocator;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 加权随机路由
 * <p>
 * 基于copy on write更新数据
 *
 * @author huangjianqin
 * @date 2021/5/7
 */
public class RandomRouter implements ProviderRouter {
    /** key -> serviceId, value -> {@link InstanceIdWeightList}, copy on write更新 */
    private UnifiedMap<Integer, InstanceIdWeightList> serviceId2InstanceIdWeightList = new UnifiedMap<>();

    @Override
    public Integer route(int serviceId) {
        InstanceIdWeightList instanceIdWeightList = serviceId2InstanceIdWeightList.get(serviceId);
        if (Objects.isNull(instanceIdWeightList)) {
            return null;
        }

        return instanceIdWeightList.weightedRandom();
    }

    @Override
    public void onAppRegistered(RSocketEndpoint rsocketEndpoint, int weight, Collection<ServiceLocator> services) {
        //copy on write
        UnifiedMap<Integer, InstanceIdWeightList> serviceId2InstanceIdWeightList = new UnifiedMap<>(this.serviceId2InstanceIdWeightList);
        int instanceId = rsocketEndpoint.getId();
        for (ServiceLocator serviceLocator : services) {
            int serviceId = serviceLocator.getId();

            InstanceIdWeightList instanceIdWeightList = serviceId2InstanceIdWeightList.get(serviceId);
            if (Objects.isNull(instanceIdWeightList)) {
                //没有, 则创建
                instanceIdWeightList = new InstanceIdWeightList();
                serviceId2InstanceIdWeightList.put(serviceId, instanceIdWeightList);
            }
            instanceIdWeightList.updateInstanceIdWeight(instanceId, weight);
        }
        this.serviceId2InstanceIdWeightList = serviceId2InstanceIdWeightList;
    }

    @Override
    public void onServiceUnregistered(int instanceId, int weight, Collection<Integer> serviceIds) {
        //copy on write
        UnifiedMap<Integer, InstanceIdWeightList> serviceId2InstanceIdWeightList = new UnifiedMap<>(this.serviceId2InstanceIdWeightList);
        for (Integer serviceId : serviceIds) {
            InstanceIdWeightList instanceIdWeightList = serviceId2InstanceIdWeightList.get(serviceId);
            if (Objects.isNull(instanceIdWeightList)) {
                continue;
            }
            instanceIdWeightList.removeInstance(instanceId);
        }
        this.serviceId2InstanceIdWeightList = serviceId2InstanceIdWeightList;
    }

    @Override
    public Collection<Integer> getAllInstanceIds(int serviceId) {
        InstanceIdWeightList instanceIdWeightList = serviceId2InstanceIdWeightList.get(serviceId);
        if (Objects.isNull(instanceIdWeightList)) {
            return Collections.emptyList();
        }
        return instanceIdWeightList.instanceIdWeights.stream().map(wii -> wii.instanceId).collect(Collectors.toSet());
    }

    /**
     * 指定service id对应的所有instance和其权重组成的list
     */
    private static class InstanceIdWeightList {
        /** app instance id和权重 */
        private List<InstanceIdWeight> instanceIdWeights = Collections.emptyList();

        /** 计算加权后各app instance的权重 */
        private List<Integer> weights = Collections.emptyList();

        /**
         * 新app instance注册, 更新缓存信息
         */
        public void updateInstanceIdWeight(int instanceId, int weight) {
            updateInstanceIdWeight(new InstanceIdWeight(instanceId, weight));
        }

        /**
         * 新app instance注册, 更新缓存信息
         */
        public void updateInstanceIdWeight(InstanceIdWeight instanceIdWeight) {
            updateInstanceIdWeights(Collections.singletonList(instanceIdWeight));
        }

        /**
         * 新app instance注册, 更新缓存信息
         */
        public void updateInstanceIdWeights(List<InstanceIdWeight> newInstanceIdWeights) {
            List<InstanceIdWeight> instanceIdWeights = new ArrayList<>(this.instanceIdWeights);
            instanceIdWeights.addAll(newInstanceIdWeights);

            updateInstanceIdWeights0(instanceIdWeights);
        }

        /**
         * 原有app instance取消注册, 移除缓存信息
         */
        public void removeInstance(int instanceId) {
            List<InstanceIdWeight> instanceIdWeights = new ArrayList<>(this.instanceIdWeights);
            instanceIdWeights.removeIf(wii -> wii.instanceId == instanceId);

            updateInstanceIdWeights0(instanceIdWeights);
        }

        /**
         * 更新缓存数据
         *
         * @param instanceIdWeights 此时存活的app instance id和weight
         */
        private void updateInstanceIdWeights0(List<InstanceIdWeight> instanceIdWeights) {
            List<Integer> weights = calculateWeights(instanceIdWeights);

            this.instanceIdWeights = instanceIdWeights;
            this.weights = weights;
        }

        /**
         * 计算加权后各app instance的权重
         */
        private List<Integer> calculateWeights(List<InstanceIdWeight> instanceIdWeights) {
            List<Integer> weights = new ArrayList<>(instanceIdWeights.size());
            for (InstanceIdWeight instanceIdWeight : instanceIdWeights) {
                weights.add(instanceIdWeight.weight);
            }

            for (int i = 1; i < weights.size(); i++) {
                weights.set(i, weights.get(i) + weights.get(i - 1));
            }

            return weights;
        }

        /**
         * @return 加权随机出app instance id
         */
        public int weightedRandom() {
            int totalWeight = weights.get(weights.size() - 1);
            int value = ThreadLocalRandom.current().nextInt(totalWeight + 1);
            return instanceIdWeights.get(binarySearchIndex(value)).instanceId;
        }

        /**
         * 二分搜索查找随机app instance index
         */
        private int binarySearchIndex(int value) {
            int low = 0;
            int high = weights.size() - 1;

            while (low <= high) {
                int mid = (low + high) >>> 1;
                long midVal = weights.get(mid);

                if (midVal < value) {
                    low = mid + 1;
                } else if (midVal > value) {
                    high = mid - 1;
                } else {
                    return mid;
                }
            }

            return low;
        }
    }

    /**
     * app instance id和weight
     */
    private static final class InstanceIdWeight {
        /** app instance id */
        private final int instanceId;
        /** 权重 */
        private final int weight;

        public InstanceIdWeight(int instanceId, int weight) {
            this.instanceId = instanceId;
            this.weight = weight;
        }

        //getter
        public int getInstanceId() {
            return instanceId;
        }

        public int getWeight() {
            return weight;
        }
    }
}
