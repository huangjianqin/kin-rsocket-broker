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
import reactor.core.publisher.Sinks;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * @author huangjianqin
 * @date 2021/3/29
 */
public class DefaultBrokerManager extends AbstractBrokerManager implements BrokerManager {
    private static final Logger log = LoggerFactory.getLogger(DefaultBrokerManager.class);
    /** 本机broker */
    private Broker localBroker;
    @Autowired
    private RSocketBrokerProperties brokerConfig;

    public DefaultBrokerManager(Sinks.Many<CloudEventData<?>> cloudEventSink) {
        super(cloudEventSink);
    }

    @PostConstruct
    public void init() {
        String localIp = NetUtils.getIp();
        String schema = "tcp";
        RSocketBrokerProperties.RSocketSSL rsocketSSL = brokerConfig.getSsl();
        if (Objects.nonNull(rsocketSSL) && rsocketSSL.isEnabled()) {
            schema = "tcps";
        }
        this.localBroker = Broker.of(RSocketAppContext.ID, schema,
                localIp, brokerConfig.getExternalDomain(), brokerConfig.getPort());
        log.info("start standalone cluster");
    }

    @Override
    public Flux<Collection<Broker>> brokersChangedFlux() {
        //单机, 不存在变化broker
        return Flux.empty();
    }

    @Override
    public Broker localBroker() {
        return localBroker;
    }

    @Override
    public Collection<Broker> all() {
        return Collections.singleton(localBroker);
    }

    @Override
    public Mono<Broker> getBroker(String ip) {
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
        //do nothing
    }

    @Override
    public Mono<String> broadcast(CloudEventData<?> cloudEvent) {
        handleCloudEvent(cloudEvent);
        //返回ip即可
        return Mono.just(localBroker.getIp());
    }
}