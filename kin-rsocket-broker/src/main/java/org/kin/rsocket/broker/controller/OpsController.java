package org.kin.rsocket.broker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.kin.rsocket.broker.ServiceResponder;
import org.kin.rsocket.broker.ServiceRouteTable;
import org.kin.rsocket.broker.ServiceRouter;
import org.kin.rsocket.broker.cluster.Broker;
import org.kin.rsocket.broker.cluster.BrokerManager;
import org.kin.rsocket.core.RSocketAppContext;
import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.event.CloudEventBuilder;
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
@RequestMapping("/ops")
public class OpsController {
    @Autowired
    private ServiceRouter serviceRouter;
    @Autowired
    private ServiceRouteTable routeTable;
    @Autowired
    private BrokerManager brokerManager;
    @Autowired
    private ObjectMapper objectMapper;

    @RequestMapping("/services")
    public Mono<Collection<ServiceLocator>> services() {
        return Mono.just(routeTable.getAllServices());
    }

    @RequestMapping("/connections")
    public Mono<Map<String, Collection<String>>> connections() {
        return Flux.fromIterable(serviceRouter.getAllResponders())
                .map(ServiceResponder::getAppMetadata)
                .collectMultimap(AppMetadata::getName, AppMetadata::getIp);
    }

    @RequestMapping("/cluster/brokers")
    public Mono<Collection<Broker>> brokers() {
        return Mono.just(brokerManager.all());
    }

    @PostMapping("/cluster/update")
    public Mono<Void> updateUpstream(@RequestBody String uris) {
        UpstreamClusterChangedEvent upstreamClusterChangedEvent =
                UpstreamClusterChangedEvent.of("", Symbols.BROKER, "", Arrays.asList(uris.split(",")));

        CloudEventData<UpstreamClusterChangedEvent> cloudEvent =
                CloudEventBuilder.builder(upstreamClusterChangedEvent)
                        .withSource(RSocketAppContext.SOURCE)
                        .build();

        return serviceRouter.broadcast(Symbols.BROKER, cloudEvent);
    }

    @PostMapping("/cluster/stopLocalBroker")
    public Mono<String> stopLocalBroker() {
        brokerManager.close();
        return Mono.just("Succeed to stop local broker from Cluster! Please shutdown app after 15 seconds!");
    }
}
