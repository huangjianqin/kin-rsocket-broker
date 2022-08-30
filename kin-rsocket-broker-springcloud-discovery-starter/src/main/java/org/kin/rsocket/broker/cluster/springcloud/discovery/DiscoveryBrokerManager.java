package org.kin.rsocket.broker.cluster.springcloud.discovery;

import org.kin.framework.utils.NetUtils;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.broker.cluster.AbstractRSocketBrokerManager;
import org.kin.rsocket.broker.cluster.BrokerInfo;
import org.kin.rsocket.broker.cluster.RSocketBrokerManager;
import org.kin.rsocket.core.event.CloudEventData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 基于spring reactive cloud discovery机制发现和监听rsocket broker集群变化
 * 目的是支持k8s
 *
 * @author huangjianqin
 * @date 2021/8/12
 */
public class DiscoveryBrokerManager extends AbstractRSocketBrokerManager implements RSocketBrokerManager, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(DiscoveryBrokerManager.class);

    /** 每N秒刷新一下rsocket broker集群信息 */
    private static final int REFRESH_INTERVAL_SECONDS = 5;
    /** spring reactive cloud discovery中broker服务名字 */
    private static final String DEFAULT_BROKER_DISCOVERY_SERVICE = "rsocket-broker";

    /** spring reactive cloud discovery client */
    private final ReactiveDiscoveryClient discoveryClient;
    /** key -> ip address, value -> rsocket brokers数据 */
    private volatile Map<String, BrokerInfo> brokers = new HashMap<>();
    /** 集群broker信息变化sink, 使用者可以监听集群变化并作出响应 */
    private final Sinks.Many<Collection<BrokerInfo>> brokersSink = Sinks.many().multicast().onBackpressureBuffer();
    /** 定时刷新集群broker信息Flux的Disposable */
    private final Disposable brokersRefresher;

    public DiscoveryBrokerManager(ReactiveDiscoveryClient discoveryClient) {
        this(discoveryClient, DEFAULT_BROKER_DISCOVERY_SERVICE, REFRESH_INTERVAL_SECONDS);
    }

    public DiscoveryBrokerManager(ReactiveDiscoveryClient discoveryClient, String brokerDiscoveryService) {
        this(discoveryClient, brokerDiscoveryService, REFRESH_INTERVAL_SECONDS);
    }

    public DiscoveryBrokerManager(ReactiveDiscoveryClient discoveryClient, int internal) {
        this(discoveryClient, DEFAULT_BROKER_DISCOVERY_SERVICE, internal);
    }

    public DiscoveryBrokerManager(ReactiveDiscoveryClient discoveryClient, String brokerDiscoveryService, int internal) {
        if (StringUtils.isBlank(brokerDiscoveryService)) {
            brokerDiscoveryService = DEFAULT_BROKER_DISCOVERY_SERVICE;
        }
        if (internal <= 0) {
            internal = REFRESH_INTERVAL_SECONDS;
        }

        this.discoveryClient = discoveryClient;
        String finalBrokerDiscoveryService = brokerDiscoveryService;
        this.brokersRefresher = Flux.interval(Duration.ofSeconds(internal))
                .flatMap(aLong -> this.discoveryClient.getInstances(finalBrokerDiscoveryService).collectList())
                .subscribe(serviceInstances -> {
                    boolean changed = serviceInstances.size() != brokers.size();
                    for (ServiceInstance serviceInstance : serviceInstances) {
                        if (!brokers.containsKey(serviceInstance.getHost())) {
                            changed = true;
                        }
                    }
                    if (changed) {
                        brokers = serviceInstances.stream().map(serviceInstance -> {
                            BrokerInfo broker = new BrokerInfo();
                            broker.setIp(serviceInstance.getHost());
                            return broker;
                        }).collect(Collectors.toMap(BrokerInfo::getIp, bi -> bi));
                        log.info(String.format("RSocket Cluster server list changed: %s", String.join(",", brokers.keySet())));
                        brokersSink.tryEmitNext(brokers.values());
                    }
                });
    }

    @Override
    public Flux<Collection<BrokerInfo>> brokersChangedFlux() {
        return brokersSink.asFlux();
    }

    @Override
    public BrokerInfo localBroker() {
        return brokers.get(NetUtils.getIp());
    }

    @Override
    public Collection<BrokerInfo> all() {
        return brokers.values();
    }

    @Override
    public Mono<BrokerInfo> getBroker(String ip) {
        if (brokers.containsKey(ip)) {
            return Mono.just(brokers.get(ip));
        } else {
            return Mono.empty();
        }
    }

    @Override
    public Boolean isStandAlone() {
        return false;
    }

    @Override
    public Mono<String> broadcast(CloudEventData<?> cloudEvent) {
        //TODO 目前还不支持broker间广播事件, 考虑CloudEventNotifyService??
        return Mono.empty();
    }

    @Override
    public void close() {
        brokersRefresher.dispose();
    }

    @Override
    public void destroy() {
        close();
    }
}