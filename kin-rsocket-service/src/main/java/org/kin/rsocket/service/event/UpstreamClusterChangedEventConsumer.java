package org.kin.rsocket.service.event;

import io.cloudevents.CloudEvent;
import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.UpstreamCluster;
import org.kin.rsocket.core.event.AbstractCloudEventConsumer;
import org.kin.rsocket.core.event.UpstreamClusterChangedEvent;
import org.kin.rsocket.service.UpstreamClusterManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author huangjianqin
 * @date 2021/3/27
 */
public final class UpstreamClusterChangedEventConsumer extends AbstractCloudEventConsumer<UpstreamClusterChangedEvent> {
    private static final Logger log = LoggerFactory.getLogger(UpstreamClusterChangedEventConsumer.class);
    /** upstream cluster manager */
    private final UpstreamClusterManager upstreamClusterManager;

    public UpstreamClusterChangedEventConsumer(UpstreamClusterManager upstreamClusterManager) {
        this.upstreamClusterManager = upstreamClusterManager;
    }

    @Override
    public void consume(CloudEvent cloudEvent, UpstreamClusterChangedEvent event) {
        String serviceId = ServiceLocator.gsv(event.getGroup(), event.getInterfaceName(), event.getVersion());
        UpstreamCluster upstreamCluster = upstreamClusterManager.get(serviceId);
        if (upstreamCluster != null) {
            log.info(String.format("RSocket Broker Topology updated for '%s' with '%s'", serviceId, String.join(",", event.getUris())));
            upstreamCluster.refreshUris(event.getUris());
        }
    }
}
