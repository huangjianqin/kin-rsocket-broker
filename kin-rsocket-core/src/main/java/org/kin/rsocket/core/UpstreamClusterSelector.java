package org.kin.rsocket.core;

/**
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
