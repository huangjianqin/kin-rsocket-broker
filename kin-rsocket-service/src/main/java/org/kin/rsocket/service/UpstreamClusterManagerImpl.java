package org.kin.rsocket.service;

import com.google.common.collect.ImmutableSet;
import org.kin.framework.utils.CollectionUtils;
import org.kin.framework.utils.ExceptionUtils;
import org.kin.rsocket.core.RSocketAppContext;
import org.kin.rsocket.core.RSocketRequesterSupport;
import org.kin.rsocket.core.UpstreamCluster;
import org.kin.rsocket.core.discovery.DiscoveryService;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.CloudEventSupport;
import org.kin.rsocket.core.event.P2pServiceChangedEvent;
import org.kin.rsocket.core.utils.Symbols;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

/**
 * 管理broker或者上流服务的rsocket connection
 *
 * @author huangjianqin
 * @date 2021/3/27
 */
final class UpstreamClusterManagerImpl implements UpstreamClusterManager {
    private static final Logger log = LoggerFactory.getLogger(UpstreamClusterManagerImpl.class);
    /** 每N min请求broker discovery service 获取broker集群uri信息 */
    private static final int BROKER_URIS_REFRESH_INTERNAL = 120;
    /** 每N min请求broker discovery service 获取p2p服务uri信息 */
    private static final int P2P_URIS_REFRESH_INTERNAL = 120;

    /** upstream requester配置 */
    private final RSocketRequesterSupport requesterSupport;
    /** upstream clusters */
    private final Map<String, UpstreamCluster> clusters = new ConcurrentHashMap<>();
    /** broker upstream cluster */
    private UpstreamCluster brokerCluster;
    /** broker的服务发现reference, 用于获取broker集群信息 */
    private volatile DiscoveryService brokerDiscoveryService;
    /** 开启p2p的服务gsv */
    private final CopyOnWriteArraySet<String> p2pServiceIds = new CopyOnWriteArraySet<>();
    /**
     * loadbalance策略
     *
     * @see org.kin.rsocket.core.upstream.loadbalance.UpstreamLoadBalance
     */
    private final String loadBalance;

    UpstreamClusterManagerImpl(RSocketRequesterSupport requesterSupport) {
        this(requesterSupport, null);
    }

    UpstreamClusterManagerImpl(RSocketRequesterSupport requesterSupport, String loadBalance) {
        this.requesterSupport = requesterSupport;
        this.loadBalance = loadBalance;

        if (requesterSupport instanceof RSocketRequesterSupportImpl) {
            ((RSocketRequesterSupportImpl) requesterSupport).setUpstreamClusterManager(this);
        }
    }

    @Override
    public void add(String group,
                    String serviceName,
                    String version,
                    List<String> uris) {
        UpstreamCluster cluster = new UpstreamCluster(group, serviceName, version, requesterSupport, uris, loadBalance);
        clusters.put(cluster.getServiceId(), cluster);
        if (cluster.isBroker()) {
            this.brokerCluster = cluster;
            monitorClusters();
        }
    }

    @Override
    public void add(RSocketServiceProperties rsocketServiceProperties) {
        if (rsocketServiceProperties.getBrokers() != null && !rsocketServiceProperties.getBrokers().isEmpty()) {
            if (rsocketServiceProperties.getJwtToken() == null || rsocketServiceProperties.getJwtToken().isEmpty()) {
                try {
                    throw new JwtTokenNotFoundException();
                } catch (JwtTokenNotFoundException e) {
                    ExceptionUtils.throwExt(e);
                }
            }
            //添加rsocket service broker connection
            add(null, Symbols.BROKER, null, rsocketServiceProperties.getBrokers());
        }
        if (rsocketServiceProperties.getEndpoints() != null && !rsocketServiceProperties.getEndpoints().isEmpty()) {
            //添加rsocket service endpoint connection
            for (EndpointProperties endpointProperties : rsocketServiceProperties.getEndpoints()) {
                add(endpointProperties.getGroup(),
                        endpointProperties.getService(),
                        endpointProperties.getVersion(),
                        endpointProperties.getUris());
            }
        }
    }

    @Override
    public Collection<UpstreamCluster> getAll() {
        return clusters.values();
    }

    @Override
    public UpstreamCluster get(String serviceId) {
        return clusters.get(serviceId);
    }

    @Override
    public UpstreamCluster getBroker() {
        return this.brokerCluster;
    }

    @Override
    public void refresh(String serviceId, List<String> uris) {
        clusters.get(serviceId).refreshUris(uris);
    }

    @Override
    public void close() {
        for (UpstreamCluster cluster : clusters.values()) {
            cluster.close();
        }
    }

    @Override
    public RSocketRequesterSupport getRequesterSupport() {
        return requesterSupport;
    }

    @Override
    public void remove(String serviceId) {
        UpstreamCluster removed = clusters.remove(serviceId);
        if (Objects.nonNull(removed)) {
            //30s后关闭链接
            Mono.delay(Duration.ofSeconds(30)).subscribe(timestamp -> removed.close());
        }
    }

    @Override
    public void openP2p(String... gsvs) {
        p2pServiceIds.addAll(Arrays.asList(gsvs));

        //通知broker更新开启的p2p服务
        CloudEventData<CloudEventSupport> cloudEventData = P2pServiceChangedEvent.of(RSocketAppContext.ID, getP2pServices()).toCloudEvent();
        UpstreamCluster broker = getBroker();
        if (Objects.isNull(broker)) {
            //与broker建立了连接才通知
            return;
        }
        broker.broadcastCloudEvent(cloudEventData).subscribe();
    }

    @Override
    public Set<String> getP2pServices() {
        return ImmutableSet.copyOf(p2pServiceIds);
    }

    private DiscoveryService findBrokerDiscoveryService() {
        if (Objects.nonNull(brokerCluster) && Objects.isNull(brokerDiscoveryService)) {
            this.brokerDiscoveryService = RSocketServiceReferenceBuilder.requester(DiscoveryService.class)
                    .callTimeout(3000)
                    .upstreamClusterManager(this)
                    .build();
        }
        return brokerDiscoveryService;
    }

    /**
     * 监控上游集群
     */
    private void monitorClusters() {
        if (Objects.nonNull(brokerCluster)) {
            monitorBroker();
            monitorP2pService();
        }
    }

    /**
     * 监控broker集群
     */
    private void monitorBroker() {
        //broker 与service不在同一台机器, 则downstream需要开启定时刷新broker uris, 防止broker挂了, 一直不刷新broker集群信息
        boolean refresh = false;
        List<String> brokerUris = brokerCluster.getUris();
        if (brokerUris != null && brokerUris.size() == 1) {
            String host = URI.create(brokerUris.get(0)).getHost();
            refresh = !host.equals("127.0.0.1") && !host.equals("localhost");
        }

        if (refresh) {
            /**
             * 支持幂等监控broker集群信息变化
             * 每N min请求broker获取broker集群信息, 以防{@link org.kin.rsocket.core.event.UpstreamClusterChangedEvent}事件丢失
             */
            Flux.interval(Duration.ofSeconds(BROKER_URIS_REFRESH_INTERNAL))
                    .flatMap(timestamp -> findBrokerDiscoveryService().getInstances(Symbols.BROKER).collectList())
                    .map(serviceInstances -> serviceInstances.stream().map(serviceInstance -> serviceInstance.getUri().toString()).collect(Collectors.toList()))
                    .subscribe(uris -> brokerCluster.refreshUris(uris));
        }
    }

    /**
     * 监控broker集群
     */
    private void monitorP2pService() {
        if (CollectionUtils.isNonEmpty(p2pServiceIds)) {
            // interval sync to p2p service instances list
            Flux.interval(Duration.ofSeconds(P2P_URIS_REFRESH_INTERNAL))
                    .flatMap(timestamp -> Flux.fromIterable(p2pServiceIds).flatMap(gsv -> findBrokerDiscoveryService()
                            .getInstances(gsv)
                            .collectList()
                            .map(serviceInstances -> Tuples.of(gsv, serviceInstances.stream().map(serviceInstance -> serviceInstance.getUri().toString()).collect(Collectors.toList())))))
                    .subscribe(tuple -> {
                        List<String> uris = tuple.getT2();
                        if (!uris.isEmpty()) {
                            UpstreamCluster upstreamCluster = get(tuple.getT1());
                            if (upstreamCluster != null) {
                                upstreamCluster.refreshUris(uris);
                            }
                        }
                    });
        }
    }

    @Override
    public UpstreamCluster select(String serviceId) {
        //1. 根据对应serviceId寻找
        UpstreamCluster upstreamCluster = get(serviceId);
        if (Objects.isNull(upstreamCluster)) {
            //2. 默认回退到broker
            upstreamCluster = getBroker();
        }
        return upstreamCluster;
    }
}
