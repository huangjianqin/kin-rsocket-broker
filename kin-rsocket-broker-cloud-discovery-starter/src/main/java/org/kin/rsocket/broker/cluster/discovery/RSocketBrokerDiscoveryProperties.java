package org.kin.rsocket.broker.cluster.discovery;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author huangjianqin
 * @date 2021/8/12
 */
@ConfigurationProperties(prefix = "kin.rsocket.broker.k8s")
public class RSocketBrokerDiscoveryProperties {
    /** spring cloud k8s服务实例名 */
    private String service;
    /** 集群broker信息刷新间隔 */
    private int refreshInternal;

    //setter && getter
    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public int getRefreshInternal() {
        return refreshInternal;
    }

    public void setRefreshInternal(int refreshInternal) {
        this.refreshInternal = refreshInternal;
    }
}
