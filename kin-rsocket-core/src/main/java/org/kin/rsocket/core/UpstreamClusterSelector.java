package org.kin.rsocket.core;

/**
 * requester端
 * 上游服务(broker)集群选择策略
 *
 * @author huangjianqin
 * @date 2021/8/13
 */
@FunctionalInterface
public interface UpstreamClusterSelector {
    /**
     * 选择一个合适的{@link UpstreamCluster}
     *
     * @param serviceId rsocket service gsv
     */
    UpstreamCluster select(String serviceId);
}
