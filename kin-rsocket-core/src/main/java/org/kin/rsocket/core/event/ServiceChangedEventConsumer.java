package org.kin.rsocket.core.event;

import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.UpstreamCluster;
import org.kin.rsocket.core.UpstreamClusterManager;
import org.kin.rsocket.core.event.application.UpstreamClusterChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * @author huangjianqin
 * @date 2021/3/27
 */
public class ServiceChangedEventConsumer implements CloudEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(ServiceChangedEventConsumer.class);
    /** upstream cluster manager */
    private final UpstreamClusterManager upstreamClusterManager;

    public ServiceChangedEventConsumer(UpstreamClusterManager upstreamClusterManager) {
        this.upstreamClusterManager = upstreamClusterManager;
    }

    @Override
    public boolean shouldAccept(CloudEventData<?> cloudEvent) {
        String type = cloudEvent.getAttributes().getType();
        return UpstreamClusterChangedEvent.class.getCanonicalName().equalsIgnoreCase(type);
    }

    @Override
    public Mono<Void> consume(CloudEventData<?> cloudEvent) {
        return Mono.fromRunnable(() -> consume0(cloudEvent));
    }

    private void consume0(CloudEventData<?> cloudEvent) {
        UpstreamClusterChangedEvent clusterChangedEvent = CloudEventSupport.unwrapData(cloudEvent, UpstreamClusterChangedEvent.class);
        if (clusterChangedEvent != null) {
            String serviceId = ServiceLocator.gsv(clusterChangedEvent.getGroup(), clusterChangedEvent.getInterfaceName(), clusterChangedEvent.getVersion());
            UpstreamCluster upstreamCluster = upstreamClusterManager.get(serviceId);
            if (upstreamCluster != null) {
                upstreamCluster.refreshUris(clusterChangedEvent.getUris());
                log.info(String.format("RSocket Broker Topology updated for '%s' with '%s'", serviceId, String.join(",", clusterChangedEvent.getUris())));
            }
        }
    }
}
