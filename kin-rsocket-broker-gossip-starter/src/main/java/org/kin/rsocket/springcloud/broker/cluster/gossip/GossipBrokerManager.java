package org.kin.rsocket.springcloud.broker.cluster.gossip;

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
import org.kin.framework.event.HandleEvent;
import org.kin.framework.utils.NetUtils;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.broker.RSocketBrokerProperties;
import org.kin.rsocket.broker.cluster.AbstractBrokerManager;
import org.kin.rsocket.broker.cluster.BrokerInfo;
import org.kin.rsocket.broker.cluster.BrokerManager;
import org.kin.rsocket.core.MetricsNames;
import org.kin.rsocket.core.RSocketAppContext;
import org.kin.rsocket.core.event.CloudEventBuilder;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.utils.JSON;
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
 * 基于Gossip的rsocket broker manager
 *
 * @author huangjianqin
 * @date 2021/3/29
 */
@HandleEvent
public class GossipBrokerManager extends AbstractBrokerManager implements BrokerManager, ClusterMessageHandler, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(GossipBrokerManager.class);
    /** 请求新增broker数据(也就是{@link BrokerInfo})的gossip header key */
    private static final String BROKER_INFO_HEADER = "brokerInfo";
    /** 通过gossip广播cloud event的gossip header key */
    private static final String CLOUD_EVENT_HEADER = "cloudEvent";

    /** broker配置 */
    private final RSocketBrokerProperties brokerConfig;
    /** gossip配置 */
    private final RSocketBrokerGossipProperties gossipConfig;

    /** gossip cluster */
    private Mono<Cluster> cluster;
    /** 本机broker数据 */
    private BrokerInfo localBrokerInfo;
    /** key -> ip address, value -> rsocket brokers数据 */
    private final Map<String, BrokerInfo> brokers = new HashMap<>();
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
                .membership(membershipConfig -> membershipConfig.seedMembers(seedMembers()).syncInterval(5_000))
                .transport(transportConfig ->
                        transportConfig
                                .transportFactory(new TcpTransportFactory())
                                .messageCodec(JacksonMessageCodec.INSTANCE)
                                .port(gossipPort))
                .handler(cluster1 -> this)
                .start();
        //subscribe and start & join the cluster
        cluster.subscribe();
        RSocketBrokerProperties.RSocketSSL sslConfig = brokerConfig.getSsl();
        this.localBrokerInfo = BrokerInfo.of(RSocketAppContext.ID, Objects.nonNull(sslConfig) && sslConfig.isEnabled() ? "tcps" : "tcp",
                localIp, brokerConfig.getExternalDomain(), brokerConfig.getPort());
        brokers.put(localIp, localBrokerInfo);
        log.info("start cluster with Gossip support");

        Metrics.globalRegistry.gauge(MetricsNames.CLUSTER_BROKER_COUNT, this, manager -> manager.brokers.size());
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
    public void onMessage(Message message) {
        if (message.header(BROKER_INFO_HEADER) != null) {
            Message replyMessage = Message.builder()
                    .correlationId(message.correlationId())
                    .data(localBrokerInfo)
                    .build();
            this.cluster.flatMap(cluster -> cluster.send(message.sender(), replyMessage)).subscribe();
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void onGossip(Message gossip) {
        String cloudEventHeader = gossip.header(CLOUD_EVENT_HEADER);
        if (StringUtils.isNotBlank(cloudEventHeader)) {
            try {
                Class<?> cloudEventClass = Class.forName(cloudEventHeader);
                String json = gossip.data();
                handleCloudEvent(CloudEventBuilder.builder(JSON.read(json, cloudEventClass)).build());
            } catch (Exception e) {
                log.error("gossip broadcast cloud event error", e);
            }
        }
    }

    @Override
    public Mono<String> broadcast(CloudEventData<?> cloudEvent) {
        Message message = Message.builder()
                .header(CLOUD_EVENT_HEADER, cloudEvent.getAttributes().getType())
                .data(JSON.write(cloudEvent.getData()))
                .build();
        return cluster.flatMap(cluster -> cluster.spreadGossip(message));
    }

    private Mono<BrokerInfo> makeJsonRpcCall(Member member) {
        String uuid = UUID.randomUUID().toString();
        Message jsonRpcMessage = Message.builder()
                .correlationId(uuid)
                .header(BROKER_INFO_HEADER, "true")
                .build();
        return cluster.flatMap(cluster -> cluster.requestResponse(member, jsonRpcMessage)).map(Message::data);
    }

    @Override
    public void onMembershipEvent(MembershipEvent event) {
        Member member = event.member();
        String brokerIp = member.address().host();
        int brokerPort = member.address().port();
        if (event.isAdded()) {
            makeJsonRpcCall(member).subscribe(rsocketBroker -> {
                brokers.put(brokerIp, rsocketBroker);
                log.info("Broker '{}:{}' added from cluster", brokerIp, brokerPort);
            });
        } else if (event.isRemoved()) {
            brokers.remove(brokerIp);
            log.info("Broker '{}:{}' removed from cluster", brokerIp, brokerPort);
        } else if (event.isLeaving()) {
            brokers.remove(brokerIp);
            log.info("Broker '{}:{}' left from cluster", brokerIp, brokerPort);
        }
        brokersSink.tryEmitNext(brokers.values());
    }

    @Override
    public void close() {
        cluster.subscribe(Cluster::shutdown);
        brokersSink.tryEmitComplete();
    }

    @Override
    public void destroy() {
        close();
    }
}

