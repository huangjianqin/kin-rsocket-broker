package org.kin.rsocket.service.event;

import org.kin.framework.utils.CollectionUtils;
import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.UpstreamCluster;
import org.kin.rsocket.core.event.AbstractCloudEventConsumer;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.ServiceInstanceChangedEvent;
import org.kin.rsocket.service.UpstreamClusterManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

/**
 * @author huangjianqin
 * @date 2021/8/13
 */
public final class ServiceInstanceChangedEventConsumer extends AbstractCloudEventConsumer<ServiceInstanceChangedEvent> {
    private static final Logger log = LoggerFactory.getLogger(ServiceInstanceChangedEventConsumer.class);
    private final UpstreamClusterManager upstreamClusterManager;

    public ServiceInstanceChangedEventConsumer(UpstreamClusterManager upstreamClusterManager) {
        this.upstreamClusterManager = upstreamClusterManager;
    }

    @Override
    protected Mono<Void> consume(CloudEventData<?> cloudEventData, ServiceInstanceChangedEvent cloudEvent) {
        return Mono.fromRunnable(() -> consume0(cloudEvent));
    }

    private void consume0(ServiceInstanceChangedEvent cloudEvent) {
        if (Objects.isNull(cloudEvent)) {
            return;
        }

        String group = cloudEvent.getGroup();
        String service = cloudEvent.getService();
        String version = cloudEvent.getVersion();
        String serviceId = ServiceLocator.gsv(group, service, version);
        List<String> uris = cloudEvent.getUris();
        UpstreamCluster upstreamCluster = upstreamClusterManager.get(serviceId);
        String uriChangeLog = String.format("RSocket Broker Topology updated for %s with %s", serviceId, String.join(",", uris));
        if (Objects.nonNull(upstreamCluster)) {
            if (CollectionUtils.isEmpty(uris)) {
                //rsocket service下线
                upstreamClusterManager.remove(upstreamCluster);
            } else {
                //rsocket service uris变化
                upstreamCluster.refreshUris(uris);
            }
            log.info(uriChangeLog);
        } else {
            if (CollectionUtils.isNonEmpty(uris)) {
                try {
                    upstreamClusterManager.add(group, service, version, uris);
                    log.info(uriChangeLog);
                } catch (Exception e) {
                    log.error(String.format("Failed to connect URI: %s", String.join(",", uris)));
                }
            }
        }
    }
}
