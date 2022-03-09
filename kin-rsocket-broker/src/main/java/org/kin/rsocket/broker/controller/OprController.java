package org.kin.rsocket.broker.controller;

import org.kin.rsocket.broker.RSocketEndpoint;
import org.kin.rsocket.broker.RSocketServiceManager;
import org.kin.rsocket.broker.cluster.BrokerInfo;
import org.kin.rsocket.broker.cluster.RSocketBrokerManager;
import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.UpstreamClusterChangedEvent;
import org.kin.rsocket.core.metadata.AppMetadata;
import org.kin.rsocket.core.utils.Symbols;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 * broker操作controller
 *
 * @author huangjianqin
 * @date 2021/3/31
 */
@RestController
@RequestMapping("/opr")
public class OprController {
    @Autowired
    private RSocketServiceManager serviceManager;
    @Autowired
    private RSocketBrokerManager brokerManager;

    @RequestMapping("/services")
    public Mono<Collection<ServiceLocator>> services() {
        return Mono.just(serviceManager.getAllServices());
    }

    @RequestMapping("/connections")
    public Mono<Map<String, Collection<String>>> connections() {
        return Flux.fromIterable(serviceManager.getAllRSocketEndpoints())
                .map(RSocketEndpoint::getAppMetadata)
                .collectMultimap(AppMetadata::getName, AppMetadata::getIp);
    }

    @RequestMapping("/cluster/brokers")
    public Mono<Collection<BrokerInfo>> brokers() {
        return Mono.just(brokerManager.all());
    }

    @PostMapping("/cluster/update")
    public Mono<Void> updateUpstream(@RequestBody String uris) {
        UpstreamClusterChangedEvent upstreamClusterChangedEvent =
                UpstreamClusterChangedEvent.of("", Symbols.BROKER, "", Arrays.asList(uris.split(",")));

        CloudEventData<UpstreamClusterChangedEvent> cloudEvent = upstreamClusterChangedEvent.toCloudEvent();

        return serviceManager.broadcast(Symbols.BROKER, cloudEvent);
    }

    @PostMapping("/cluster/stop")
    public Mono<String> stopLocalBroker() {
        brokerManager.close();
        return Mono.just("Succeed to stop local broker from Cluster! Please shutdown app after 15 seconds!");
    }
}
