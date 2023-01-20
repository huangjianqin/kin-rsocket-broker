package org.kin.rsocket.service;

import org.kin.rsocket.core.RSocketRequesterSupport;
import org.kin.rsocket.core.UpstreamCluster;
import org.kin.rsocket.core.UpstreamClusterSelector;
import reactor.core.Disposable;

import java.util.*;

/**
 * @author huangjianqin
 * @date 2021/4/18
 */
public interface UpstreamClusterManager extends UpstreamClusterSelector, Disposable {
    /**
     * 添加upstream cluster
     */
    default void add(String group,
                     String service,
                     String version) {
        add(group, service, version, Collections.emptyList());
    }

    /**
     * 添加upstream cluster
     */
    void add(String group,
             String service,
             String version,
             List<String> uris);

    /**
     * 解析{@link RSocketServiceProperties}配置并注册创建对应的{@link UpstreamCluster}
     */
    void add(RSocketServiceProperties rsocketServiceProperties);

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
     * @return 底层connection使用的 {@link RSocketRequesterSupport}实例
     */
    RSocketRequesterSupport getRequesterSupport();

    /**
     * 移除指定{@link UpstreamCluster}
     */
    default void remove(UpstreamCluster upstreamCluster) {
        if (Objects.isNull(upstreamCluster)) {
            return;
        }

        remove(upstreamCluster.getServiceId());
    }

    /**
     * 移除指定serviceId的{@link UpstreamCluster}
     */
    void remove(String serviceId);

    /**
     * 添加需要开启p2p的服务gsv
     *
     * @param gsvs 需要开启p2p的服务gsv列表
     */
    void openP2p(String... gsvs);

    /**
     * @return 开启p2p的服务gsv
     */
    Set<String> getP2pServices();
}
