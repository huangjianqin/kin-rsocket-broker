package org.kin.rsocket.core;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.core.RSocketConnector;
import io.rsocket.exceptions.ConnectionErrorException;
import io.rsocket.plugins.RSocketInterceptor;
import io.rsocket.util.ByteBufPayload;
import org.kin.framework.utils.CollectionUtils;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.CloudEventRSocket;
import org.kin.rsocket.core.event.CloudEventReply;
import org.kin.rsocket.core.event.broker.ServicesExposedEvent;
import org.kin.rsocket.core.health.ServiceHealth;
import org.kin.rsocket.core.metadata.GSVRoutingMetadata;
import org.kin.rsocket.core.metadata.MessageMimeTypeMetadata;
import org.kin.rsocket.core.metadata.RSocketCompositeMetadata;
import org.kin.rsocket.core.metadata.RSocketMimeType;
import org.kin.rsocket.core.transport.UriTransportRegistry;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.net.ConnectException;
import java.net.URI;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.*;
import java.util.function.Predicate;

/**
 * @author huangjianqin
 * @date 2021/3/27
 */
@SuppressWarnings("unchecked")
public class LoadBalanceRequester extends AbstractRSocket implements CloudEventRSocket, RequesterRsocket {
    private static final Logger log = LoggerFactory.getLogger(LoadBalanceRequester.class);
    /** health check interval seconds */
    private static final int HEALTH_CHECK_INTERVAL_SECONDS = 15;
    /** unhealth uris reconnect minutes */
    private static final int UNHEALTH_URIS_RECONNECT_MINS = 5;
    /** consumer rsocket延迟remove, 秒数 */
    private static final int CONSUMER_REMOVE_DELAY = 15;
    /** provider rsocket延迟remove, 秒数 */
    private static final int PROVIDER_REMOVE_DELAY = 45;
    /** 判断connection error逻辑 */
    private static final Predicate<? super Throwable> CONNECTION_ERROR_PREDICATE =
            e -> e instanceof ClosedChannelException || e instanceof ConnectionErrorException || e instanceof ConnectException;
    /** 刷新uri时连接失败, 则1分钟内尝试12重连, 间隔5s */
    private static final int retryCount = 12;

    /** load balance rule */
    private Selector selector;
    /** service id */
    private final String serviceId;
    /** 上次刷新的rsocket uris */
    private Collection<String> lastRefreshRSocketUris = new ArrayList<>();
    /** 有效的rsocket连接 */
    private Map<String, RSocket> activeRSockets = Collections.emptyMap();
    /** unhealthy uris */
    private final Set<String> unhealthyUris = new HashSet<>();
    /** 上一次health check时间 */
    private long lastHealthCheckTimestamp;
    /** 上一次refresh uri 时间 */
    private long lastRefreshTimestamp;
    /** requester配置 */
    private final RequesterSupport requesterSupport;
    /** health check 元数据bytes, 避免多次创建bytes */
    private final ByteBuf healthCheckCompositeByteBuf;
    /** 是否是service provider */
    private boolean isServiceProvider = false;
    /** health check disposable */
    private Disposable healthCheckDisposable;
    /** unhealth uris check disposable */
    private Disposable unhealthUrisCheckDisposable;

    /**
     * load balance rule为random的requester
     */
    public static LoadBalanceRequester random(String serviceId,
                                              Flux<Collection<String>> urisFactory,
                                              RequesterSupport requesterSupport) {
        return new LoadBalanceRequester(serviceId, Selector.RANDOM, urisFactory, requesterSupport);
    }

    /**
     * load balance rule为round robin的requester
     */
    public static LoadBalanceRequester roundRobin(String serviceId,
                                                  Flux<Collection<String>> urisFactory,
                                                  RequesterSupport requesterSupport) {
        return new LoadBalanceRequester(serviceId, new RoundRobinSelector(), urisFactory, requesterSupport);
    }

    public LoadBalanceRequester(String serviceId,
                                Selector selector,
                                Flux<Collection<String>> urisFactory,
                                RequesterSupport requesterSupport) {
        this.serviceId = serviceId;
        this.selector = selector;
        this.requesterSupport = requesterSupport;
        if (!requesterSupport.exposedServices().get().isEmpty()) {
            this.isServiceProvider = true;
        }
        urisFactory.subscribe(this::refreshRSockets);
        //health check composite metadata
        RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(
                GSVRoutingMetadata.of(null, ServiceHealth.class.getCanonicalName(), "check", null),
                MessageMimeTypeMetadata.of(RSocketMimeType.Java_Object));
        ByteBuf compositeMetadataContent = compositeMetadata.getContent();
        this.healthCheckCompositeByteBuf = Unpooled.copiedBuffer(compositeMetadataContent);
        ReferenceCountUtil.safeRelease(compositeMetadataContent);

        //start health check timer
        startHealthCheck();
        //start check and reconnect unhealthy uris
        startUnhealthyUrisCheck();
    }

    /** 刷新Rsocket实例 */
    private void refreshRSockets(Collection<String> rsocketUris) {
        //no changes
        if (CollectionUtils.isSame(this.lastRefreshRSocketUris, rsocketUris)) {
            return;
        }
        this.lastRefreshTimestamp = System.currentTimeMillis();
        this.lastRefreshRSocketUris = rsocketUris;
        this.unhealthyUris.clear();
        Flux.fromIterable(rsocketUris)
                .flatMap(rsocketUri -> {
                    if (activeRSockets.containsKey(rsocketUri)) {
                        return Mono.just(Tuples.of(rsocketUri, activeRSockets.get(rsocketUri)));
                    } else {
                        return connect(rsocketUri)
                                //health check after connection
                                .flatMap(rsocket -> healthCheck(rsocket, rsocketUri).map(payload -> Tuples.of(rsocketUri, rsocket)))
                                .doOnError(error -> {
                                    log.error(String.format("connect '%s' error", rsocketUri), error);
                                    this.unhealthyUris.add(rsocketUri);
                                    tryToReconnect(rsocketUri, error);
                                });
                    }
                })
                .collectList()
                .subscribe(tupleRsockets -> {
                    if (tupleRsockets.isEmpty()) {
                        return;
                    }
                    Map<String, RSocket> newActiveRSockets = new HashMap<>();
                    for (Tuple2<String, RSocket> tuple : tupleRsockets) {
                        newActiveRSockets.put(tuple.getT1(), tuple.getT2());
                    }

                    //被移除的uri
                    Map<String, RSocket> staleRSockets = new HashMap<>();
                    for (Map.Entry<String, RSocket> entry : activeRSockets.entrySet()) {
                        if (!newActiveRSockets.containsKey(entry.getKey())) {
                            staleRSockets.put(entry.getKey(), entry.getValue());
                        }
                    }

                    //新增的uri
                    Map<String, RSocket> newAddedRSockets = new HashMap<>();
                    for (Map.Entry<String, RSocket> entry : newActiveRSockets.entrySet()) {
                        if (!activeRSockets.containsKey(entry.getKey())) {
                            newAddedRSockets.put(entry.getKey(), entry.getValue());
                        }
                    }

                    this.activeRSockets = newActiveRSockets;

                    //close所有被移除的rsockets
                    if (!staleRSockets.isEmpty()) {
                        //consumer优先移除, 然后再是provider
                        int delaySeconds = this.isServiceProvider ? PROVIDER_REMOVE_DELAY : CONSUMER_REMOVE_DELAY;
                        Flux.fromIterable(staleRSockets.entrySet())
                                .delaySubscription(Duration.ofSeconds(delaySeconds))
                                .subscribe(entry -> {
                                    log.info(String.format("delay remove invalid rsocket(uri='%s')", entry.getKey()));
                                    entry.getValue().dispose();
                                });
                    }

                    //subscribe rsocket close event
                    for (Map.Entry<String, RSocket> entry : newAddedRSockets.entrySet()) {
                        entry.getValue().onClose().subscribe(aVoid -> {
                            onRSocketClosed(entry.getKey(), entry.getValue(), null);
                        });
                    }
                });
    }

    /**
     * 根据{@link LoadBalanceRequester#selector}选择一个有效的RSocket
     */
    private RSocket next() {
        return selector.select(new ArrayList<>(activeRSockets.values()));
    }

    /**
     * @return requester disposed但仍然调用方法异常的Mono实例
     */
    private Mono<?> disposedMono() {
        return Mono.error(new IllegalStateException("requester is disposed"));
    }

    /**
     * @return requester disposed但仍然调用方法异常的Flux实例
     */
    private Flux<?> disposedFlux() {
        return Flux.error(new IllegalStateException("requester is disposed"));
    }


    @Override
    public Mono<Payload> requestResponse(Payload payload) {
        if (isDisposed()) {
            return (Mono<Payload>) disposedMono();
        }
        RSocket next = next();
        if (next == null) {
            ReferenceCountUtil.safeRelease(payload);
            return Mono.error(new NoAvailableConnectionException(serviceId));
        }
        return next.requestResponse(payload)
                .onErrorResume(CONNECTION_ERROR_PREDICATE, error -> {
                    onRSocketClosed(next);
                    return requestResponse(payload);
                });
    }


    @Override
    public Mono<Void> fireAndForget(Payload payload) {
        if (isDisposed()) {
            return (Mono<Void>) disposedMono();
        }
        RSocket next = next();
        if (next == null) {
            ReferenceCountUtil.safeRelease(payload);
            return Mono.error(new NoAvailableConnectionException(serviceId));
        }
        return next.fireAndForget(payload)
                .onErrorResume(CONNECTION_ERROR_PREDICATE, error -> {
                    onRSocketClosed(next);
                    return fireAndForget(payload);
                });
    }

    @Override
    public Mono<Void> fireCloudEvent(CloudEventData<?> cloudEvent) {
        if (isDisposed()) {
            return (Mono<Void>) disposedMono();
        }
        try {
            Payload payload = cloudEvent2Payload(cloudEvent);
            return metadataPush(payload);
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    @Override
    public Mono<Void> fireCloudEventReply(URI replayTo, CloudEventReply eventReply) {
        if (isDisposed()) {
            return (Mono<Void>) disposedMono();
        }
        return fireAndForget(cloudEventReply2Payload(replayTo, eventReply));
    }

    @Override
    public Mono<Void> broadcastCloudEvent(CloudEventData<?> cloudEvent) {
        if (isDisposed()) {
            return (Mono<Void>) disposedMono();
        }
        try {
            return Flux.fromIterable(activeRSockets.values())
                    .flatMap(rsocket -> rsocket.metadataPush(cloudEvent2Payload(cloudEvent)))
                    .doOnError(throwable -> log.error("Failed to fire event to all upstream nodes", throwable))
                    .then();
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    @Override
    public Flux<Payload> requestStream(Payload payload) {
        if (isDisposed()) {
            return (Flux<Payload>) disposedFlux();
        }
        RSocket next = next();
        if (next == null) {
            ReferenceCountUtil.safeRelease(payload);
            return Flux.error(new NoAvailableConnectionException(serviceId));
        }
        return next.requestStream(payload)
                .onErrorResume(CONNECTION_ERROR_PREDICATE, error -> {
                    onRSocketClosed(next);
                    return requestStream(payload);
                });
    }

    @Override
    public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
        if (isDisposed()) {
            return (Flux<Payload>) disposedFlux();
        }
        RSocket next = next();
        if (next == null) {
            return Flux.error(new NoAvailableConnectionException(serviceId));
        }
        return next.requestChannel(payloads)
                .onErrorResume(CONNECTION_ERROR_PREDICATE, error -> {
                    onRSocketClosed(next);
                    return requestChannel(payloads);
                });
    }

    @Override
    public Mono<Void> metadataPush(Payload payload) {
        if (isDisposed()) {
            return (Mono<Void>) disposedMono();
        }
        return Flux.fromIterable(activeRSockets.values()).flatMap(rSocket -> rSocket.metadataPush(payload)).then();
    }

    @Override
    public void dispose() {
        super.dispose();
        healthCheckDisposable.dispose();
        unhealthUrisCheckDisposable.dispose();
        for (RSocket rsocket : activeRSockets.values()) {
            try {
                rsocket.dispose();
            } catch (Exception ignore) {

            }
        }
        activeRSockets.clear();
    }

    @Override
    public void refreshUnhealthyUris() {
        if (isDisposed()) {
            return;
        }
        for (String unhealthyUri : unhealthyUris) {
            tryToReconnect(unhealthyUri, null);
        }
    }

    /**
     * 连接closed逻辑处理
     * 发起请求时, 发现连接无效, 则
     */
    private void onRSocketClosed(RSocket rsocket) {
        for (Map.Entry<String, RSocket> entry : activeRSockets.entrySet()) {
            if (entry.getValue() == rsocket) {
                onRSocketClosed(entry.getKey(), entry.getValue(), null);
            }
        }
        if (!rsocket.isDisposed()) {
            rsocket.dispose();
        }
    }

    /**
     * 连接closed逻辑处理
     */
    private void onRSocketClosed(String rsocketUri, RSocket rsocket, Throwable cause) {
        if (this.lastRefreshRSocketUris.contains(rsocketUri)) {
            this.unhealthyUris.add(rsocketUri);
            if (activeRSockets.containsKey(rsocketUri)) {
                activeRSockets.remove(rsocketUri);
                if (Objects.nonNull(cause)) {
                    log.error(String.format("connection '%s' closed, cause by", rsocketUri), cause);
                } else {
                    log.info("connection '%s' closed");
                }
                tryToReconnect(rsocketUri, cause);
            }
            if (!rsocket.isDisposed()) {
                rsocket.dispose();
            }
        }
    }

    /**
     * 重连成功后, 刷新数据, 并注册暴露的服务
     */
    private void onRSocketReconnected(String rsocketUri, RSocket rsocket) {
        this.activeRSockets.put(rsocketUri, rsocket);
        this.unhealthyUris.remove(rsocketUri);
        rsocket.onClose().subscribe(aVoid -> onRSocketClosed(rsocketUri, rsocket, null));
        CloudEventData<ServicesExposedEvent> cloudEvent = requesterSupport.servicesExposedEvent().get();
        if (cloudEvent != null) {
            Payload payload = cloudEvent2Payload(cloudEvent);
            rsocket.metadataPush(payload).subscribe();
        }
    }

    /**
     * 刷新uri时连接失败, 5s后尝试重连
     */
    private void tryToReconnect(String rsocketUri, Throwable error) {
        //try to reconnect every 5 seconds in 1 minute if connection error
        if (CONNECTION_ERROR_PREDICATE.test(error)) {
            Flux.range(1, retryCount)
                    .delayElements(Duration.ofSeconds(5))
                    .filter(id -> activeRSockets.isEmpty() || !activeRSockets.containsKey(rsocketUri))
                    .subscribe(count -> {
                        if (LoadBalanceRequester.this.isDisposed()) {
                            return;
                        }
                        connect(rsocketUri)
                                .flatMap(rsocket -> healthCheck(rsocket, rsocketUri).map(payload -> rsocket))
                                .doOnError(e -> {
                                    unhealthyUris.add(rsocketUri);
                                    log.error(String.format("reconnect '%s' error %d times", rsocketUri, count), e);
                                })
                                .subscribe(rsocket -> {
                                    onRSocketReconnected(rsocketUri, rsocket);
                                    log.info(String.format("reconnect '%s' success", rsocketUri));
                                });
                    });
        }
    }

    /**
     * create connect
     */
    private Mono<RSocket> connect(String uri) {
        if (LoadBalanceRequester.this.isDisposed()) {
            throw new IllegalStateException("requester is disposed");
        }

        try {
            //requesterInterceptors
            RSocketConnector rsocketConnector = RSocketConnector.create();
            for (RSocketInterceptor requestInterceptor : requesterSupport.requesterInterceptors()) {
                rsocketConnector.interceptors(interceptorRegistry -> {
                    interceptorRegistry.forRequester(requestInterceptor);
                });
            }
            //responderInterceptors
            for (RSocketInterceptor responderInterceptor : requesterSupport.responderInterceptors()) {
                rsocketConnector.interceptors(interceptorRegistry -> {
                    interceptorRegistry.forResponder(responderInterceptor);
                });
            }
            Payload payload = requesterSupport.setupPayload().get();
            return rsocketConnector
                    .setupPayload(payload)
                    .metadataMimeType(RSocketMimeType.CompositeMetadata.getType())
                    .dataMimeType(RSocketMimeType.Java_Object.getType())
                    .acceptor(requesterSupport.socketAcceptor())
                    .connect(UriTransportRegistry.INSTANCE.client(uri))
                    .doOnError(error -> ReferenceCountUtil.safeRelease(payload));
        } catch (Exception e) {
            log.error(String.format("connect '%s' error", uri), e);
            return Mono.error(new ConnectionErrorException(uri));
        }
    }

    /**
     * 每{@link LoadBalanceRequester#HEALTH_CHECK_INTERVAL_SECONDS}秒检查connection是否连接
     */
    private void startHealthCheck() {
        this.lastHealthCheckTimestamp = System.currentTimeMillis();
        healthCheckDisposable = Flux.interval(Duration.ofSeconds(HEALTH_CHECK_INTERVAL_SECONDS))
                .flatMap(timestamp -> Flux.fromIterable(activeRSockets.entrySet()))
                .subscribe(entry -> {
                    healthCheck(entry.getValue(), entry.getKey()).doOnError(error -> {
                        if (CONNECTION_ERROR_PREDICATE.test(error)) {
                            //connection closed
                            onRSocketClosed(entry.getKey(), entry.getValue(), error);
                        }
                    }).subscribe();
                });
    }

    /**
     * 每{@link LoadBalanceRequester#UNHEALTH_URIS_RECONNECT_MINS}分钟尝试重连
     */
    private void startUnhealthyUrisCheck() {
        unhealthUrisCheckDisposable = Flux.interval(Duration.ofMinutes(UNHEALTH_URIS_RECONNECT_MINS))
                .filter(sequence -> !unhealthyUris.isEmpty())
                .subscribe(entry -> {
                    for (String unhealthyUri : unhealthyUris) {
                        if (!activeRSockets.containsKey(unhealthyUri)) {
                            connect(unhealthyUri)
                                    .flatMap(rsocket -> healthCheck(rsocket, unhealthyUri).map(payload -> rsocket))
                                    .subscribe(rsocket -> {
                                        onRSocketReconnected(unhealthyUri, rsocket);
                                        log.info("requester reconnect '{}'", unhealthyUri);
                                    });
                        }
                    }
                });
    }

    /**
     * health check
     */
    private Mono<Boolean> healthCheck(RSocket rsocket, String url) {
        return rsocket.requestResponse(ByteBufPayload.create(Unpooled.EMPTY_BUFFER, healthCheckCompositeByteBuf.retainedDuplicate()))
                .timeout(Duration.ofSeconds(HEALTH_CHECK_INTERVAL_SECONDS))
                .handle((payload, sink) -> {
                    //todo 逻辑是否需要修正
                    byte indicator = payload.data().readByte();
                    // check error code: hessian decode: 1: -111,  0-> -112, -1 -> -113
                    if (indicator == -111) {
                        sink.next(true);
                    } else {
                        sink.error(new Exception("Health check failed :" + url));
                    }
                });
    }

    //getter
    public Set<String> getUnhealthyUris() {
        return unhealthyUris;
    }

    public Collection<String> getLastRefreshRSocketUris() {
        return lastRefreshRSocketUris;
    }

    public long getLastHealthCheckTimestamp() {
        return lastHealthCheckTimestamp;
    }

    public long getLastRefreshTimestamp() {
        return lastRefreshTimestamp;
    }

    public Map<String, RSocket> getActiveRSockets() {
        return activeRSockets;
    }
}