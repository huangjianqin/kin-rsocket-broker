package org.kin.rsocket.broker.cluster.gossip;

import io.scalecube.cluster.Cluster;
import io.scalecube.cluster.ClusterImpl;
import io.scalecube.cluster.ClusterMessageHandler;
import io.scalecube.cluster.Member;
import io.scalecube.cluster.codec.jackson.JacksonMessageCodec;
import io.scalecube.cluster.membership.MembershipEvent;
import io.scalecube.cluster.transport.api.Message;
import io.scalecube.net.Address;
import org.kin.framework.event.HandleEvent;
import org.kin.framework.utils.NetUtils;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.broker.RSocketBrokerProperties;
import org.kin.rsocket.broker.cluster.AbstractRSocketBrokerManager;
import org.kin.rsocket.broker.cluster.RSocketBroker;
import org.kin.rsocket.broker.cluster.RSocketBrokerManager;
import org.kin.rsocket.core.RSocketAppContext;
import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.utils.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于Gossip的rsocket broker manager
 *
 * @author huangjianqin
 * @date 2021/3/29
 */
@HandleEvent
public class GossipBrokerManager extends AbstractRSocketBrokerManager implements RSocketBrokerManager, ClusterMessageHandler, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(GossipBrokerManager.class);
    /** 请求新增broker数据(也就是{@link RSocketBroker})的gossip header key */
    private static final String BROKER_INFO_HEADER = "brokerInfo";
    /** 通过gossip广播cloud event的gossip header key */
    private static final String CLOUD_EVENT_HEADER = "cloudEvent";

    @Autowired
    private RSocketBrokerProperties brokerConfig;
    @Autowired
    private RSocketBrokerGossipProperties gossipConfig;

    /** gossip cluster */
    private Mono<Cluster> cluster;
    /** 本机broker数据 */
    private RSocketBroker localBroker;
    /** key -> ip address, value -> rsocket brokers数据 */
    private final Map<String, RSocketBroker> brokers = new HashMap<>();
    /** brokers changes emitter processor */
    private final EmitterProcessor<Collection<RSocketBroker>> brokersEmitterProcessor = EmitterProcessor.create();

    @PostConstruct
    public void init() {
        String localIp = NetUtils.getIp();
        int gossipPort = gossipConfig.getGossipPort();
        cluster = new ClusterImpl()
                .config(clusterConfig -> clusterConfig.externalHost(localIp).externalPort(gossipPort))
                .membership(membershipConfig -> membershipConfig.seedMembers(seedMembers()).syncInterval(5_000))
                //todo 默认使用jdk序列化, 现在使用json, 看看需不需要修改
                .transport(transportConfig ->
                        transportConfig
                                .messageCodec(JacksonMessageCodec.INSTANCE)
                                .port(gossipPort))
                .handler(cluster1 -> this)
                .start();
        //subscribe and start & join the cluster
        cluster.subscribe();
        this.localBroker = RSocketBroker.of(RSocketAppContext.ID, brokerConfig.getSsl().isEnabled() ? "tcps" : "tcp",
                localIp, brokerConfig.getExternalDomain(), brokerConfig.getPort());
        brokers.put(localIp, localBroker);
        log.info("start cluster with Gossip support");
    }

    /**
     * gossip member host
     */
    private List<Address> seedMembers() {
        //从upstream broker host获取
        return brokerConfig.getUpstreamBrokers().stream()
                .map(host -> Address.create(host, gossipConfig.getGossipPort()))
                .collect(Collectors.toList());
    }

    @Override
    public Flux<Collection<RSocketBroker>> brokersChangedFlux() {
        return brokersEmitterProcessor;
    }

    @Override
    public Collection<RSocketBroker> all() {
        return brokers.values();
    }

    @Override
    public RSocketBroker localBroker() {
        return localBroker;
    }

    @Override
    public Mono<RSocketBroker> getBroker(String ip) {
        if (brokers.containsKey(ip)) {
            return Mono.just(brokers.get(ip));
        } else {
            return Mono.empty();
        }
    }

    @Override
    public Flux<ServiceLocator> getServices(String ip) {
        //todo
        return Flux.empty();
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
                    .data(localBroker)
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
                handleCloudEvent((CloudEventData) JSON.read(json, cloudEventClass));
            } catch (Exception e) {
                log.error("gossip broadcast cloud event error", e);
            }
        }
    }

    @Override
    public Mono<String> broadcast(CloudEventData<?> cloudEvent) {
        Message message = Message.builder()
                .header(CLOUD_EVENT_HEADER, cloudEvent.getAttributes().getType())
                .data(JSON.write(cloudEvent))
                .build();
        return cluster.flatMap(cluster -> cluster.spreadGossip(message));
    }

    public Mono<RSocketBroker> makeJsonRpcCall(Member member) {
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
        if (event.isAdded()) {
            makeJsonRpcCall(member).subscribe(rsocketBroker -> {
                brokers.put(brokerIp, rsocketBroker);
                log.info(String.format("Broker '%s' added from cluster", brokerIp));
            });
        } else if (event.isRemoved()) {
            brokers.remove(brokerIp);
            log.info(String.format("Broker '%s' removed from cluster", brokerIp));
        } else if (event.isLeaving()) {
            brokers.remove(brokerIp);
            log.info(String.format("Broker '%s' left from cluster", brokerIp));
        }
        brokersEmitterProcessor.onNext(brokers.values());
    }

    private RSocketBroker memberToBroker(Member member) {
        RSocketBroker broker = new RSocketBroker();
        broker.setIp(member.address().host());
        return broker;
    }

    @Override
    public void close() {
        cluster.subscribe(Cluster::shutdown);
        brokersEmitterProcessor.onComplete();
    }

    @Override
    public void destroy() {
        close();
    }
}

