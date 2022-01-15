package org.kin.rsocket.broker;

import io.netty.buffer.ByteBuf;
import io.rsocket.RSocket;
import io.rsocket.loadbalance.WeightedStats;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.multimap.list.FastListMultimap;
import org.jctools.maps.NonBlockingHashMap;
import org.kin.framework.collection.Tuple;
import org.kin.rsocket.core.ServiceLocator;
import reactor.util.annotation.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author huangjianqin
 * @date 2022/1/15
 * @see io.rsocket.loadbalance.WeightedLoadbalanceStrategy
 */
public class WeightedStatsRouter implements ProviderRouter {
    private static final double EXP_FACTOR = 4.0;

    /** 存储每个{@link RSocket} connector的状态信息, 用于计算其权重 */
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
        //有效的rsocket service instance id
        List<Integer> vaildInstanceIds = new ArrayList<>();
        //有效的rsocket service instance及对应的WeightedStats
        Map<Integer, Tuple<RSocket, WeightedStats>> partStatsMap = new HashMap<>();

        for (WeightedInstance weightedInstance : weightedInstances) {
            int instanceId = weightedInstance.instanceId;
            WeightedStats stats = statsMap.get(weightedInstance.requester);
            if (Objects.isNull(stats)) {
                continue;
            }
            vaildInstanceIds.add(instanceId);
            partStatsMap.put(instanceId, new Tuple<>(weightedInstance.requester, stats));
        }

        int size = vaildInstanceIds.size();
        switch (size) {
            case 1:
                return vaildInstanceIds.get(0);
            case 2: {
                Integer f = vaildInstanceIds.get(0);
                Tuple<RSocket, WeightedStats> ft = partStatsMap.get(f);
                Integer s = vaildInstanceIds.get(1);
                Tuple<RSocket, WeightedStats> st = partStatsMap.get(s);
                double w1 = algorithmicWeight(ft.first(), ft.second());
                double w2 = algorithmicWeight(st.first(), st.second());
                if (w1 < w2) {
                    return s;
                } else {
                    return f;
                }
            }
            default: {
                int f = 0;
                Tuple<RSocket, WeightedStats> ft = null;
                int s = 0;
                Tuple<RSocket, WeightedStats> st = null;
                for (int i = 0; i < maxPairSelectionAttempts; i++) {
                    int i1 = ThreadLocalRandom.current().nextInt(size);
                    int i2 = ThreadLocalRandom.current().nextInt(size - 1);

                    if (i2 >= i1) {
                        i2++;
                    }

                    f = vaildInstanceIds.get(i1);
                    ft = partStatsMap.get(f);
                    s = vaildInstanceIds.get(i2);
                    st = partStatsMap.get(s);

                    if (ft.first().availability() > 0.0 && st.first().availability() > 0.0) {
                        break;
                    }
                }

                if (ft != null & st != null) {
                    double w1 = algorithmicWeight(ft.first(), ft.second());
                    double w2 = algorithmicWeight(st.first(), st.second());
                    if (w1 < w2) {
                        return s;
                    } else {
                        return f;
                    }
                } else if (ft != null) {
                    return f;
                } else {
                    return s;
                }
            }
        }
    }

    private static double algorithmicWeight(
            RSocket rSocket, @Nullable WeightedStats weightedStats) {
        if (weightedStats == null || rSocket.isDisposed() || rSocket.availability() == 0.0) {
            return 0.0;
        }
        int pending = weightedStats.pending();

        double latency = weightedStats.predictedLatency();

        double low = weightedStats.lowerQuantileLatency();
        double high =
                Math.max(
                        weightedStats.higherQuantileLatency(),
                        low * 1.001); // ensure higherQuantile > lowerQuantile + .1%
        double bandWidth = Math.max(high - low, 1);

        if (latency < low) {
            latency /= calculateFactor(low, latency, bandWidth);
        } else if (latency > high) {
            latency *= calculateFactor(latency, high, bandWidth);
        }

        return (rSocket.availability() * weightedStats.weightedAvailability())
                / (1.0d + latency * (pending + 1));
    }

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
    public void onServiceUnregistered(BrokerResponder responder, int weight, Collection<Integer> serviceIds) {
        //copy on write
        Integer instanceId = responder.getId();
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
