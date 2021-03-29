package org.kin.rsocket.broker.cluster;

import org.kin.framework.utils.NetUtils;
import org.kin.rsocket.broker.RSocketBrokerProperties;
import org.kin.rsocket.core.RSocketAppContext;
import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.event.CloudEventData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.Collections;

/**
 * @author huangjianqin
 * @date 2021/3/29
 */
public class DefaultBrokerManager extends AbstractRSocketBrokerManager implements RSocketBrokerManager {
    private static final Logger log = LoggerFactory.getLogger(DefaultBrokerManager.class);
    /** 本机broker */
    private RSocketBroker localBroker;
    @Autowired
    private RSocketBrokerProperties brokerConfig;

    @PostConstruct
    public void init() {
        String localIp = NetUtils.getIp();
        this.localBroker = RSocketBroker.of(RSocketAppContext.ID, brokerConfig.getSsl().isEnabled() ? "tcps" : "tcp",
                localIp, brokerConfig.getExternalDomain(), brokerConfig.getPort());
        log.info("start standalone cluster");
    }

    @Override
    public Flux<Collection<RSocketBroker>> brokersChangedFlux() {
        //单机, 不存在变化broker
        return Flux.empty();
    }

    @Override
    public RSocketBroker localBroker() {
        return localBroker;
    }

    @Override
    public Collection<RSocketBroker> all() {
        return Collections.singleton(localBroker);
    }

    @Override
    public Mono<RSocketBroker> getBroker(String ip) {
        return Mono.just(localBroker);
    }

    @Override
    public Flux<ServiceLocator> getServices(String ip) {
        return Flux.empty();
    }

    @Override
    public Boolean isStandAlone() {
        return true;
    }

    @Override
    public void close() {

    }

    @Override
    public Mono<String> broadcast(CloudEventData<?> cloudEvent) {
        handleCloudEvent(cloudEvent);
        //返回ip即可
        return Mono.just(localBroker.getIp());
    }
}