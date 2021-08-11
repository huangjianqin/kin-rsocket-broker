package org.kin.rsocket.springcloud.broker.cluster.discovery;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author huangjianqin
 * @date 2021/8/12
 */
@ConfigurationProperties(prefix = "kin.rsocket.broker.k8s")
public class RSocketBrokerDiscoveryProperties {
    /** spring cloud k8s服务实例名 */
    private String serviceName;
    /** 集群broker信息刷新间隔 */
    private int refreshInternal;

    //setter && getter
    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public int getRefreshInternal() {
        return refreshInternal;
    }

    public void setRefreshInternal(int refreshInternal) {
        this.refreshInternal = refreshInternal;
    }
}
