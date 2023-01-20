package org.kin.rsocket.broker.cluster.gossip;

import io.cloudevents.CloudEvent;
import io.micrometer.core.instrument.Metrics;
import io.scalecube.cluster.Cluster;
import io.scalecube.cluster.ClusterImpl;
import io.scalecube.cluster.ClusterMessageHandler;
import io.scalecube.cluster.Member;
import io.scalecube.cluster.codec.jackson.JacksonMessageCodec;
import io.scalecube.cluster.membership.MembershipEvent;
import io.scalecube.cluster.transport.api.Message;
import io.scalecube.net.Address;
import io.scalecube.transport.netty.tcp.TcpTransportFactory;
import org.eclipse.collections.impl.map.mutable.UnifiedMap;
import org.kin.framework.utils.NetUtils;
import org.kin.rsocket.broker.RSocketBrokerProperties;
import org.kin.rsocket.broker.cluster.AbstractRSocketBrokerManager;
import org.kin.rsocket.broker.cluster.BrokerInfo;
import org.kin.rsocket.broker.cluster.RSocketBrokerManager;
import org.kin.rsocket.core.MetricsNames;
import org.kin.rsocket.core.RSocketAppContext;
import org.kin.rsocket.core.utils.JSON;
import org.kin.rsocket.core.utils.RetryNonSerializedEmitFailureHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 基于gossip发现和监听rsocket broker集群变化
 *
 * @author huangjianqin
 * @date 2021/3/29
 */
public class GossipBrokerManager extends AbstractRSocketBrokerManager implements RSocketBrokerManager, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(GossipBrokerManager.class);
    /** 请求新增broker数据(也就是{@link BrokerInfo})的gossip header key */
    private static final String BROKER_INFO_HEADER = "brokerInfo";

    /** broker配置 */
    private final RSocketBrokerProperties brokerConfig;
    /** gossip配置 */
    private final RSocketBrokerGossipProperties gossipConfig;

    /** gossip cluster */
    private Mono<Cluster> cluster;
    /** 本机broker数据 */
    private BrokerInfo localBrokerInfo;
    /** key -> ip address, value -> rsocket brokers数据 */
    private final Map<String, BrokerInfo> brokers = new UnifiedMap<>();
    /** 集群broker信息变化sink, 使用者可以监听集群变化并作出响应 */
    private final Sinks.Many<Collection<BrokerInfo>> brokersSink = Sinks.many().multicast().onBackpressureBuffer();

    public GossipBrokerManager(RSocketBrokerProperties brokerConfig, RSocketBrokerGossipProperties gossipConfig) {
        this.brokerConfig = brokerConfig;
        this.gossipConfig = gossipConfig;
    }

    @PostConstruct
    public void init() {
        String localIp = NetUtils.getIp();
        int gossipPort = gossipConfig.getPort();
        cluster = new ClusterImpl()
                .config(clusterConfig -> clusterConfig.externalHost(localIp).externalPort(gossipPort))
                .membership(membershipConfig -> membershipConfig.seedMembers(seedMembers())
                        .namespace(gossipConfig.getNamespace())
                        .syncInterval(5_000))
                .transport(transportConfig ->
                        transportConfig
                                .transportFactory(new TcpTransportFactory())
                                .messageCodec(JacksonMessageCodec.INSTANCE)
                                .port(gossipPort))
                .handler(cluster1 -> new GossipMessageHandler())
                .start();
        //subscribe and start & join the cluster
        cluster.subscribe();
        RSocketBrokerProperties.RSocketSSL sslConfig = brokerConfig.getSsl();
        this.localBrokerInfo = BrokerInfo.of(RSocketAppContext.ID, Objects.nonNull(sslConfig) && sslConfig.isEnabled() ? "tcps" : "tcp",
                localIp, brokerConfig.getExternalDomain(), brokerConfig.getPort(), RSocketAppContext.webPort);
        brokers.put(localIp, localBrokerInfo);
        log.info("start cluster with Gossip support");

        Metrics.gauge(MetricsNames.CLUSTER_BROKER_NUM, this, manager -> manager.brokers.size());
    }

    /**
     * gossip member host
     */
    private List<Address> seedMembers() {
        //从upstream broker host获取
        return Stream.of(gossipConfig.getSeeds())
                .map(hostPort -> {
                    String[] splits = hostPort.split(":");
                    return Address.create(splits[0], Integer.parseInt(splits[1]));
                })
                .collect(Collectors.toList());
    }

    @Override
    public Flux<Collection<BrokerInfo>> brokersChangedFlux() {
        return brokersSink.asFlux();
    }

    @Override
    public Collection<BrokerInfo> all() {
        return brokers.values();
    }

    @Override
    public BrokerInfo localBroker() {
        return localBrokerInfo;
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
    public Mono<String> broadcast(CloudEvent cloudEvent) {
        Message message = Message.builder()
                .data(JSON.serializeCloudEvent(cloudEvent))
                .build();
        return cluster.flatMap(cluster -> cluster.spreadGossip(message));
    }

    private Mono<BrokerInfo> requestBrokerMember(Member member) {
        Message request = Message.builder()
                //req-resp需要带correlationId
                .correlationId(UUID.randomUUID().toString())
                .header(BROKER_INFO_HEADER, "true")
                .build();
        return cluster.flatMap(cluster -> cluster.requestResponse(member, request))
                .map(Message::data);
    }

    @Override
    public void dispose() {
        cluster.subscribe(Cluster::shutdown);
        brokersSink.emitComplete(RetryNonSerializedEmitFailureHandler.RETRY_NON_SERIALIZED);
    }

    @Override
    public void destroy() {
        dispose();
    }

    //-------------------------------------------------------------------------------------------

    /**
     * gossip消息处理
     */
    private class GossipMessageHandler implements ClusterMessageHandler {
        @Override
        public void onMessage(Message message) {
            if (message.header(BROKER_INFO_HEADER) != null) {
                //目前仅支持请求broker信息
                Message replyMessage = Message.builder()
                        .correlationId(message.correlationId())
                        .data(localBrokerInfo)
                        .build();
                cluster.flatMap(cluster -> cluster.send(message.sender(), replyMessage)).subscribe();
            }
        }

        @Override
        public void onGossip(Message gossip) {
            try {
                String cloudEventJson = gossip.data();
                handleCloudEvent(JSON.deserializeCloudEvent(cloudEventJson));
            } catch (Exception e) {
                log.error("gossip broadcast cloud event error", e);
            }
        }

        @Override
        public void onMembershipEvent(MembershipEvent event) {
            Member member = event.member();
            String brokerIp = member.address().host();
            int brokerPort = member.address().port();
            if (event.isAdded()) {
                //req-resp
                requestBrokerMember(member).subscribe(brokerInfo -> {
                    brokers.put(brokerIp, brokerInfo);
                    log.info("Broker '{}:{}' added from cluster", brokerIp, brokerPort);
                });
            } else if (event.isRemoved()) {
                brokers.remove(brokerIp);
                log.info("Broker '{}:{}' removed from cluster", brokerIp, brokerPort);
            } else if (event.isLeaving()) {
                brokers.remove(brokerIp);
                log.info("Broker '{}:{}' left from cluster", brokerIp, brokerPort);
            }
            brokersSink.emitNext(brokers.values(), RetryNonSerializedEmitFailureHandler.RETRY_NON_SERIALIZED);
        }
    }
}

