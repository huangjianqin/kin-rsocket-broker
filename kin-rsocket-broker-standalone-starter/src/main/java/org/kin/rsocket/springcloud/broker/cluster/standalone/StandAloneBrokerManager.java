package org.kin.rsocket.springcloud.broker.cluster.standalone;

import io.micrometer.core.instrument.Metrics;
import org.kin.framework.utils.NetUtils;
import org.kin.rsocket.broker.RSocketBrokerProperties;
import org.kin.rsocket.broker.cluster.AbstractRSocketBrokerManager;
import org.kin.rsocket.broker.cluster.BrokerInfo;
import org.kin.rsocket.broker.cluster.RSocketBrokerManager;
import org.kin.rsocket.core.MetricsNames;
import org.kin.rsocket.core.RSocketAppContext;
import org.kin.rsocket.core.UpstreamCluster;
import org.kin.rsocket.core.event.CloudEventData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * standalone broker manager
 *
 * @author huangjianqin
 * @date 2021/3/29
 */
public final class StandAloneBrokerManager extends AbstractRSocketBrokerManager implements RSocketBrokerManager {
    private static final Logger log = LoggerFactory.getLogger(StandAloneBrokerManager.class);
    /** 本地broker */
    private BrokerInfo localBrokerInfo;
    /** 本地broker配置 */
    private final RSocketBrokerProperties brokerConfig;

    public StandAloneBrokerManager(UpstreamCluster upstreamBrokers, RSocketBrokerProperties brokerConfig) {
        super(upstreamBrokers);
        this.brokerConfig = brokerConfig;
    }

    @PostConstruct
    @Override
    public void init() {
        super.init();

        String localIp = NetUtils.getIp();
        String schema = "tcp";
        RSocketBrokerProperties.RSocketSSL rsocketSSL = brokerConfig.getSsl();
        if (Objects.nonNull(rsocketSSL) && rsocketSSL.isEnabled()) {
            schema = "tcps";
        }
        this.localBrokerInfo = BrokerInfo.of(RSocketAppContext.ID, schema,
                localIp, brokerConfig.getExternalDomain(), brokerConfig.getPort());
        log.info("start standalone cluster");

        Metrics.gauge(MetricsNames.CLUSTER_BROKER_COUNT, brokerConfig.getUpstreamBrokers().size());
    }

    @Override
    public Flux<Collection<BrokerInfo>> brokersChangedFlux() {
        //单机, 不存在变化broker
        return Flux.empty();
    }

    @Override
    public BrokerInfo localBroker() {
        return localBrokerInfo;
    }

    @Override
    public Collection<BrokerInfo> all() {
        return Collections.singleton(localBrokerInfo);
    }

    @Override
    public Mono<BrokerInfo> getBroker(String ip) {
        return Mono.just(localBrokerInfo);
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
        //本broker已做处理, 本broker不需要再触发cloud event
        //返回ip即可
        return Mono.just(localBrokerInfo.getIp());
    }
}