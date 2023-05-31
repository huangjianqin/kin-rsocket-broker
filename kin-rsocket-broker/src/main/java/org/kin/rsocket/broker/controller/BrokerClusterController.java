package org.kin.rsocket.broker.controller;

import io.cloudevents.CloudEvent;
import org.kin.rsocket.broker.RSocketServiceRegistry;
import org.kin.rsocket.broker.cluster.BrokerInfo;
import org.kin.rsocket.broker.cluster.RSocketBrokerManager;
import org.kin.rsocket.core.event.UpstreamClusterChangedEvent;
import org.kin.rsocket.core.utils.Symbols;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collection;

/**
 * rsocket broker集群相关接口
 *
 * @author huangjianqin
 * @date 2021/3/31
 */
@RestController
@RequestMapping("/cluster")
public class BrokerClusterController {
    @Autowired
    private RSocketServiceRegistry serviceRegistry;
    @Autowired
    private RSocketBrokerManager brokerManager;


    @RequestMapping("/all")
    public Mono<Collection<BrokerInfo>> all() {
        return Mono.just(brokerManager.all());
    }

    @PostMapping("/refreshUpstreamBrokers")
    public Mono<Void> refreshUpstreamBrokers(@RequestBody String uris) {
        UpstreamClusterChangedEvent upstreamClusterChangedEvent =
                UpstreamClusterChangedEvent.of("", Symbols.BROKER, "", Arrays.asList(uris.split(",")));

        CloudEvent cloudEvent = upstreamClusterChangedEvent.toCloudEvent();

        return serviceRegistry.broadcast(Symbols.BROKER, cloudEvent);
    }

    @PostMapping("/stop")
    public Mono<String> stopLocalBroker() {
        brokerManager.dispose();
        return Mono.just("Succeed to stop local broker from Cluster! Please shutdown app after 15 seconds!");
    }
}
