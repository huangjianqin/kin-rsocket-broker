package org.kin.rsocket.broker.cluster;

import io.cloudevents.CloudEvent;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

/**
 * 自动发现rsocket broker, 动态支持rsocket broker水平扩展
 * <p>
 * 集群broker变化时, 会广播{@link org.kin.rsocket.core.event.UpstreamClusterChangedEvent}给rsocket service, 通知其
 * 刷新upstream rsocket broker uris
 *
 * @author huangjianqin
 * @date 2021/3/29
 */
public interface RSocketBrokerManager extends Disposable {
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
    Mono<String> broadcast(CloudEvent cloudEvent);
}
