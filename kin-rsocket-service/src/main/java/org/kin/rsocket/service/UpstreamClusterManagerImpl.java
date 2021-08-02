package org.kin.rsocket.service;

import org.kin.framework.utils.ExceptionUtils;
import org.kin.rsocket.core.RSocketRequesterSupport;
import org.kin.rsocket.core.UpstreamCluster;
import org.kin.rsocket.core.utils.Symbols;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理broker或者上流服务的rsocket connection
 *
 * @author huangjianqin
 * @date 2021/3/27
 */
final class UpstreamClusterManagerImpl implements UpstreamClusterManager {
    private static final Logger log = LoggerFactory.getLogger(UpstreamClusterManagerImpl.class);

    /** upstream requester配置 */
    private final RSocketRequesterSupport requesterSupport;
    /** upstream clusters */
    private final Map<String, UpstreamCluster> clusters = new ConcurrentHashMap<>();
    /** broker upstream cluster */
    private UpstreamCluster brokerCluster;

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
}
