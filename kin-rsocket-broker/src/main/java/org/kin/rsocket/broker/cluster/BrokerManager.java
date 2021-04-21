package org.kin.rsocket.broker.cluster;

import org.kin.framework.Closeable;
import org.kin.rsocket.core.event.CloudEventData;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

/**
 * @author huangjianqin
 * @date 2021/3/29
 */
public interface BrokerManager extends Closeable {
    /**
     * broker信息发生变更时, 会触发subscriber操作(subscribe数据为所有broker信息)
     */
    Flux<Collection<BrokerInfo>> brokersChangedFlux();

    /**
     * 本地broker
     */
    BrokerInfo localBroker();

    /**
     * 所有broker信息
     */
    Collection<BrokerInfo> all();

    /** 寻找某ip上的broker */
    Mono<BrokerInfo> getBroker(String ip);

    /** 是否单节点模式 */
    Boolean isStandAlone();

    /**
     * broker间广播cloud event
     *
     * @return Mono<String>, 即ip
     */
    Mono<String> broadcast(CloudEventData<?> cloudEvent);
}
