package org.kin.rsocket.service;

import org.kin.framework.Closeable;
import org.kin.rsocket.core.RequesterSupport;
import org.kin.rsocket.core.UpstreamCluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 管理broker或者上流服务的rsocket connection
 *
 * @author huangjianqin
 * @date 2021/3/27
 */
public class UpstreamClusterManager implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(UpstreamClusterManager.class);

    /** upstream requester配置 */
    private final RequesterSupport requesterSupport;
    /** upstream clusters */
    private Map<String, UpstreamCluster> clusters = new HashMap<>();
    /** broker upstream cluster */
    private UpstreamCluster brokerCluster;

    public UpstreamClusterManager(RequesterSupport requesterSupport) {
        this.requesterSupport = requesterSupport;
    }

    /**
     * 添加upstream cluster
     */
    public void add(String group,
                    String serviceName,
                    String version) {
        add(group, serviceName, version, Collections.emptyList());
    }

    /**
     * 添加upstream cluster
     */
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
     * 获取所有upstream cluster
     *
     * @return
     */
    public Collection<UpstreamCluster> getAll() {
        return clusters.values();
    }

    /**
     * 获取service upstream cluster
     */
    public UpstreamCluster get(String serviceId) {
        return clusters.get(serviceId);
    }

    /**
     * 获取broker upstream
     */
    public UpstreamCluster getBroker() {
        return this.brokerCluster;
    }

    /**
     * 刷新指定serviceId的upstream uris
     */
    public void refresh(String serviceId, List<String> uris) {
        clusters.get(serviceId).refreshUris(uris);
    }

    @Override
    public void close() {
        for (UpstreamCluster cluster : clusters.values()) {
            try {
                cluster.close();
                log.info("Succeed to close connection: ".concat(cluster.getServiceId()));
            } catch (Exception e) {
                log.error("Fail to close connection: ".concat(cluster.getServiceId()), e);
            }
        }
    }

    //getter
    public RequesterSupport getRequesterSupport() {
        return requesterSupport;
    }
}
