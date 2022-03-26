package org.kin.rsocket.broker.cluster;

import org.kin.rsocket.core.RSocketAppContext;
import org.kin.rsocket.core.UpstreamCluster;
import org.kin.rsocket.core.event.CloudEventData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author huangjianqin
 * @date 2021/3/29
 */
public abstract class AbstractRSocketBrokerManager implements RSocketBrokerManager {
    private final UpstreamCluster upstreamBrokers;

    protected AbstractRSocketBrokerManager(UpstreamCluster upstreamBrokers) {
        this.upstreamBrokers = upstreamBrokers;
    }

    /**
     * broker manager初始化
     */
    protected void init() {
        brokersChangedFlux().subscribe(this::refreshClusterUpstreamBrokers);
    }

    /**
     * 处理通过gossip广播的cloud event
     */
    protected void handleCloudEvent(CloudEventData<?> cloudEvent) {
        RSocketAppContext.CLOUD_EVENT_SINK.tryEmitNext(cloudEvent);
    }

    /**
     * 在集群模式下, 创建集群中与其余broker的连接
     */
    protected void refreshClusterUpstreamBrokers(Collection<BrokerInfo> brokerInfos) {
        List<String> uris = new ArrayList<>(upstreamBrokers.getUris());
        uris.addAll(brokerInfos.stream().map(BrokerInfo::getUrl).collect(Collectors.toList()));

        upstreamBrokers.refreshUris(uris);
    }
}
