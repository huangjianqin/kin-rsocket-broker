package org.kin.rsocket.core;

import io.rsocket.Payload;
import org.kin.framework.utils.CollectionUtils;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.CloudEventRSocket;
import org.kin.rsocket.core.utils.Symbols;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import javax.annotation.Nonnull;
import java.io.Closeable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author huangjianqin
 * @date 2021/3/27
 */
public final class UpstreamCluster implements CloudEventRSocket, RequesterRsocket, Closeable, org.kin.framework.Closeable, UpstreamClusterSelector {
    private static final Logger log = LoggerFactory.getLogger(UpstreamCluster.class);

    /** group */
    private final String group;
    /** service name */
    private final String service;
    /** version */
    private final String version;
    /** upstream uris  processor */
    private final Sinks.Many<Collection<String>> urisSink = Sinks.many().replay().latest();
    /** load balanced RSocket to connect service provider or broker instances */
    private final LoadBalanceRsocketRequester loadBalanceRequester;
    /** 上次刷新的uris */
    private volatile List<String> lastUris = Collections.emptyList();
    /** cluster是否stopped */
    private volatile boolean stopped;

    /**
     * broker upstream cluster
     */
    public static UpstreamCluster brokerUpstreamCluster(RSocketRequesterSupport requesterSupport) {
        return brokerUpstreamCluster(requesterSupport, Collections.emptyList());
    }

    /**
     * broker upstream cluster
     */
    public static UpstreamCluster brokerUpstreamCluster(RSocketRequesterSupport requesterSupport, List<String> uris) {
        return new UpstreamCluster("", Symbols.BROKER, "", requesterSupport, uris, null);
    }

    /**
     * broker upstream cluster
     */
    public static UpstreamCluster brokerUpstreamCluster(RSocketRequesterSupport requesterSupport, List<String> uris, String loadBalanceStrategy) {
        return new UpstreamCluster("", Symbols.BROKER, "", requesterSupport, uris, loadBalanceStrategy);
    }

    public UpstreamCluster(String group,
                           String service,
                           String version,
                           RSocketRequesterSupport requesterSupport) {
        this(group, service, version, requesterSupport, Collections.emptyList());
    }

    public UpstreamCluster(String group,
                           String service,
                           String version,
                           RSocketRequesterSupport requesterSupport,
                           List<String> uris) {
        this(group, service, version, requesterSupport, uris, null);
    }

    public UpstreamCluster(String group,
                           String service,
                           String version,
                           RSocketRequesterSupport requesterSupport,
                           List<String> uris,
                           String loadBalanceStrategy) {
        this.group = group;
        this.service = service;
        this.version = version;

        this.loadBalanceRequester = new LoadBalanceRsocketRequester(ServiceLocator.gsv(group, service, version), loadBalanceStrategy, urisSink.asFlux(), requesterSupport);
        if (CollectionUtils.isNonEmpty(uris)) {
            refreshUris(uris);
        }
    }

    /**
     * 刷新upstream rsocket uris
     */
    public void refreshUris(List<String> uris) {
        if (isDisposed()) {
            return;
        }
        //检查uris是否于上次刷新的一致
        if (CollectionUtils.isNonEmpty(lastUris) &&
                CollectionUtils.isSame(lastUris, uris)) {
            return;
        }
        lastUris = uris;
        urisSink.tryEmitNext(uris);
    }

    /**
     * 是否是broker upstream
     */
    public boolean isBroker() {
        return service.equals(Symbols.BROKER);
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
    public Mono<Void> fireCloudEvent(String cloudEventJson) {
        return loadBalanceRequester.fireCloudEvent(cloudEventJson);
    }

    @Nonnull
    @Override
    public Mono<Void> fireAndForget(@Nonnull Payload payload) {
        return loadBalanceRequester.fireAndForget(payload);
    }

    @Nonnull
    @Override
    public Mono<Payload> requestResponse(@Nonnull Payload payload) {
        return loadBalanceRequester.requestResponse(payload);
    }

    @Nonnull
    @Override
    public Flux<Payload> requestStream(@Nonnull Payload payload) {
        return loadBalanceRequester.requestStream(payload);
    }

    @Nonnull
    @Override
    public Flux<Payload> requestChannel(@Nonnull Publisher<Payload> payloads) {
        return loadBalanceRequester.requestChannel(payloads);
    }

    @Nonnull
    @Override
    public Mono<Void> metadataPush(@Nonnull Payload payload) {
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
        urisSink.tryEmitComplete();
        loadBalanceRequester.dispose();
        log.info(String.format("succeed to disconnect from the upstream '%s'", ServiceLocator.gsv(group, service, version)));
    }

    @Override
    public UpstreamCluster select(String serviceId) {
        String sourceServiceId = getServiceId();
        if (!sourceServiceId.equals(serviceId)) {
            throw new IllegalStateException(String.format("serviceId '%s' is not matched, should be '%s' ", serviceId, sourceServiceId));
        }
        return this;
    }

    //getter
    public String getGroup() {
        return group;
    }

    public String getService() {
        return service;
    }

    public String getVersion() {
        return version;
    }

    public String getServiceId() {
        return ServiceLocator.gsv(group, service, version);
    }

    public List<String> getUris() {
        return lastUris;
    }

    public LoadBalanceRsocketRequester getLoadBalanceRequester() {
        return loadBalanceRequester;
    }
}