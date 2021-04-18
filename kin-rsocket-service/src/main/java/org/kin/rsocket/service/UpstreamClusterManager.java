package org.kin.rsocket.service;

import org.kin.framework.Closeable;
import org.kin.rsocket.core.RequesterSupport;
import org.kin.rsocket.core.UpstreamCluster;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author huangjianqin
 * @date 2021/4/18
 */
public interface UpstreamClusterManager extends Closeable {
    /**
     * 添加upstream cluster
     */
    default void add(String group,
                     String serviceName,
                     String version) {
        add(group, serviceName, version, Collections.emptyList());
    }

    /**
     * 添加upstream cluster
     */
    void add(String group,
             String serviceName,
             String version,
             List<String> uris);

    /**
     * 解析{@link RSocketServiceProperties}配置并注册创建对应的{@link UpstreamCluster}
     */
    void add(RSocketServiceProperties config);

    /**
     * 获取所有upstream cluster
     */
    Collection<UpstreamCluster> getAll();

    /**
     * 获取service upstream cluster
     */
    UpstreamCluster get(String serviceId);

    /**
     * 获取broker upstream
     */
    UpstreamCluster getBroker();

    /**
     * 刷新指定serviceId的upstream uris
     */
    void refresh(String serviceId, List<String> uris);

    /**
     * @return 底层connection使用的 {@link RequesterSupport}实例
     */
    RequesterSupport getRequesterSupport();
}
