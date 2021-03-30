package org.kin.rsocket.core;

import io.rsocket.Payload;
import org.kin.framework.utils.CollectionUtils;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.CloudEventRSocket;
import org.kin.rsocket.core.event.CloudEventReply;
import org.kin.rsocket.core.utils.Symbols;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.ReplayProcessor;

import java.io.Closeable;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author huangjianqin
 * @date 2021/3/27
 */
public class UpstreamCluster implements CloudEventRSocket, RequesterRsocket, Closeable, org.kin.framework.Closeable {
    private static final Logger log = LoggerFactory.getLogger(UpstreamCluster.class);

    /** group */
    private final String group;
    /** service name */
    private final String serviceName;
    /** version */
    private final String version;
    /** upstream rsocket requester配置 */
    private final RequesterSupport requesterSupport;
    /** upstream uris  processor */
    private final ReplayProcessor<Collection<String>> urisProcessor = ReplayProcessor.cacheLast();
    /** load balanced RSocket to connect service provider or broker instances */
    private final LoadBalanceRequester loadBalanceRequester;
    /** 上次刷新的uris */
    private volatile List<String> lastUris;
    private volatile boolean stopped;

    /**
     * broker upstream cluster
     */
    public static UpstreamCluster brokerUpstreamCluster(RequesterSupport requesterSupport) {
        return brokerUpstreamCluster(requesterSupport, Collections.emptyList());
    }

    /**
     * broker upstream cluster
     */
    public static UpstreamCluster brokerUpstreamCluster(RequesterSupport requesterSupport, List<String> uris) {
        return new UpstreamCluster("", Symbols.BROKER, "", requesterSupport, uris);
    }

    public UpstreamCluster(String group,
                           String serviceName,
                           String version,
                           RequesterSupport requesterSupport) {
        this(group, serviceName, version, requesterSupport, Collections.emptyList());
    }

    public UpstreamCluster(String group,
                           String serviceName,
                           String version,
                           RequesterSupport requesterSupport,
                           List<String> uris) {
        this.group = group;
        this.serviceName = serviceName;
        this.version = version;
        this.requesterSupport = requesterSupport;

        this.loadBalanceRequester = LoadBalanceRequester.roundRobin(ServiceLocator.gsv(group, serviceName, version), urisProcessor, requesterSupport);

        if (CollectionUtils.isNonEmpty(uris)) {
            refreshUris(uris);
        }
    }

    /**
     * 刷新upstream rsocket uris
     */
    public void refreshUris(List<String> uris) {
        //检查uris是否于上次刷新的一致
        if (CollectionUtils.isSame(lastUris, uris)) {
            return;
        }
        this.lastUris = uris;
        //refresh uris
        if (isDisposed()) {
            urisProcessor.onNext(lastUris);
        }
    }

    /**
     * 是否是broker upstream
     */
    public boolean isBroker() {
        return serviceName.equals("*");
    }

    @Override
    public Mono<Void> broadcastCloudEvent(CloudEventData<?> cloudEvent) {
        return loadBalanceRequester.broadcastCloudEvent(cloudEvent);
    }

    @Override
    public void refreshUnhealthyUris() {
        loadBalanceRequester.refreshUnhealthyUris();
    }

    @Override
    public Mono<Void> fireCloudEvent(CloudEventData<?> cloudEvent) {
        return loadBalanceRequester.fireCloudEvent(cloudEvent);
    }

    @Override
    public Mono<Void> fireCloudEventReply(URI replayTo, CloudEventReply eventReply) {
        return loadBalanceRequester.fireCloudEventReply(replayTo, eventReply);
    }

    @Override
    public Mono<Void> fireAndForget(Payload payload) {
        return loadBalanceRequester.fireAndForget(payload);
    }

    @Override
    public Mono<Payload> requestResponse(Payload payload) {
        return loadBalanceRequester.requestResponse(payload);
    }

    @Override
    public Flux<Payload> requestStream(Payload payload) {
        return loadBalanceRequester.requestStream(payload);
    }

    @Override
    public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
        return loadBalanceRequester.requestChannel(payloads);
    }

    @Override
    public Mono<Void> metadataPush(Payload payload) {
        return loadBalanceRequester.metadataPush(payload);
    }

    @Override
    public void dispose() {
        close();
    }

    @Override
    public boolean isDisposed() {
        return stopped;
    }

    @Override
    public void close() {
        stopped = true;
        urisProcessor.onComplete();
        loadBalanceRequester.dispose();
        log.info("Succeed to disconnect from the ".concat(ServiceLocator.gsv(group, serviceName, version)));
    }

    //getter
    public String getServiceId() {
        return ServiceLocator.gsv(group, serviceName, version);
    }

    public List<String> getUris() {
        return lastUris;
    }

    public LoadBalanceRequester getLoadBalanceRequester() {
        return loadBalanceRequester;
    }
}