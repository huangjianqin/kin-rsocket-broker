package org.kin.rsocket.service;

import org.kin.framework.utils.ExceptionUtils;
import org.kin.rsocket.core.RSocketRequesterSupport;
import org.kin.rsocket.core.UpstreamCluster;
import org.kin.rsocket.core.discovery.DiscoveryService;
import org.kin.rsocket.core.utils.Symbols;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 管理broker或者上流服务的rsocket connection
 *
 * @author huangjianqin
 * @date 2021/3/27
 */
final class UpstreamClusterManagerImpl implements UpstreamClusterManager {
    private static final Logger log = LoggerFactory.getLogger(UpstreamClusterManagerImpl.class);
    /** 每N min请求broker discovery service 获取broker集群信息 */
    private static final int BROKER_URIS_REFRESH_INTERNAL = 120;

    /** upstream requester配置 */
    private final RSocketRequesterSupport requesterSupport;
    /** upstream clusters */
    private final Map<String, UpstreamCluster> clusters = new ConcurrentHashMap<>();
    /** broker upstream cluster */
    private UpstreamCluster brokerCluster;
    /** broker的服务发现reference, 用于获取broker集群信息 */
    private volatile DiscoveryService brokerDiscoveryService;

    UpstreamClusterManagerImpl(RSocketRequesterSupport requesterSupport) {
        this.requesterSupport = requesterSupport;
    }

    /**
     * 添加upstream cluster
     */
    @Override
    public void add(String group,
                    String serviceName,
                    String version,
                    List<String> uris) {
        UpstreamCluster cluster = new UpstreamCluster(group, serviceName, version, requesterSupport, uris);
        clusters.put(cluster.getServiceId(), cluster);
        if (cluster.isBroker()) {
            this.brokerCluster = cluster;
            monitorClusters();
        }
    }

    /**
     * 解析{@link RSocketServiceProperties}配置并注册创建对应的{@link UpstreamCluster}
     */
    @Override
    public void add(RSocketServiceProperties config) {
        if (config.getBrokers() != null && !config.getBrokers().isEmpty()) {
            if (config.getJwtToken() == null || config.getJwtToken().isEmpty()) {
                try {
                    throw new JwtTokenNotFoundException();
                } catch (JwtTokenNotFoundException e) {
                    ExceptionUtils.throwExt(e);
                }
            }
            //添加rsocket service broker connection
            add(null, Symbols.BROKER, null, config.getBrokers());
        }
        if (config.getEndpoints() != null && !config.getEndpoints().isEmpty()) {
            //添加rsocket service endpoint connection
            for (EndpointProperties endpointProperties : config.getEndpoints()) {
                add(endpointProperties.getGroup(),
                        endpointProperties.getService(),
                        endpointProperties.getVersion(),
                        endpointProperties.getUris());
            }
        }
    }

    /**
     * 获取所有upstream cluster
     */
    @Override
    public Collection<UpstreamCluster> getAll() {
        return clusters.values();
    }

    /**
     * 获取service upstream cluster
     */
    @Override
    public UpstreamCluster get(String serviceId) {
        return clusters.get(serviceId);
    }

    /**
     * 获取broker upstream
     */
    @Override
    public UpstreamCluster getBroker() {
        return this.brokerCluster;
    }

    /**
     * 刷新指定serviceId的upstream uris
     */
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
     * 监控broker集群
     */
    private void monitorClusters() {
        if (Objects.nonNull(brokerCluster)) {
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
    }
}
