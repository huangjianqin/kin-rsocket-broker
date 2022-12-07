package org.kin.rsocket.broker.event;

import io.cloudevents.CloudEvent;
import org.kin.rsocket.core.UpstreamCluster;
import org.kin.rsocket.core.event.AbstractCloudEventConsumer;
import org.kin.rsocket.core.event.UpstreamClusterChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;

/**
 * @author huangjianqin
 * @date 2021/3/30
 */
public final class UpstreamClusterChangedEventConsumer extends AbstractCloudEventConsumer<UpstreamClusterChangedEvent> {
    private static final Logger log = LoggerFactory.getLogger(UpstreamClusterChangedEventConsumer.class);
    @Autowired(required = false)
    /** broker upstream cluster */
    private UpstreamCluster brokerUpstreamCluster;

    @Override
    public void consume(CloudEvent cloudEvent, UpstreamClusterChangedEvent event) {
        if (Objects.isNull(brokerUpstreamCluster)) {
            return;
        }

        log.info(String.format("RSocket Broker Topology updated for '%s' with '%s'", "UpstreamBroker", String.join(",", event.getUris())));
        brokerUpstreamCluster.refreshUris(event.getUris());
    }
}
