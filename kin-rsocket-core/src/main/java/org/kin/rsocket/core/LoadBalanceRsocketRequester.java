package org.kin.rsocket.core;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.core.RSocketConnector;
import io.rsocket.exceptions.ConnectionErrorException;
import io.rsocket.frame.decoder.PayloadDecoder;
import io.rsocket.plugins.RSocketInterceptor;
import io.rsocket.util.ByteBufPayload;
import org.kin.framework.collection.ConcurrentHashSet;
import org.kin.framework.utils.CollectionUtils;
import org.kin.rsocket.core.codec.Codecs;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.CloudEventRSocket;
import org.kin.rsocket.core.event.CloudEventSupport;
import org.kin.rsocket.core.event.RSocketServicesExposedEvent;
import org.kin.rsocket.core.health.HealthCheck;
import org.kin.rsocket.core.metadata.GSVRoutingMetadata;
import org.kin.rsocket.core.metadata.RSocketCompositeMetadata;
import org.kin.rsocket.core.transport.UriTransportRegistry;
import org.kin.rsocket.core.utils.Symbols;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.net.ConnectException;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.*;
import java.util.function.Predicate;

/**
 * @author huangjianqin
 * @date 2021/3/27
 */
@SuppressWarnings("unchecked")
public class LoadBalanceRsocketRequester extends AbstractRSocket implements CloudEventRSocket, RequesterRsocket {
    private static final Logger log = LoggerFactory.getLogger(LoadBalanceRsocketRequester.class);
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
    private static final int RETRY_COUNT = 12;
    /** upstream uri刷新逻辑单线程处理 */
    private static final Scheduler REFRESH_SCHEDULER = Schedulers.newSingle("LoadBalanceRequester-refreshRSockets");

    /** load balance rule */
    private final Selector selector;
    /** service id */
    private final String serviceId;
    /** 上一次refresh uri 时间 */
    private volatile long lastRefreshTimestamp;
    /** 上次刷新的rsocket uris */
    private volatile Collection<String> lastRefreshRSocketUris = Collections.emptyList();
    /** 有效的rsocket连接, 一写, 多读, copy-on-write */
    private volatile Map<String, RSocket> activeRSockets = Collections.emptyMap();
    /** unhealthy uris */
    private final Set<String> unhealthyUris = new ConcurrentHashSet<>();
    /** 上一次health check时间 */
    private volatile long lastHealthCheckTimestamp;
    /** requester配置 */
    private final RSocketRequesterSupport requesterSupport;
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
    public static LoadBalanceRsocketRequester random(String serviceId,
                                                     Flux<Collection<String>> urisFactory,
                                                     RSocketRequesterSupport requesterSupport) {
        return new LoadBalanceRsocketRequester(serviceId, Selector.RANDOM, urisFactory, requesterSupport);
    }

    /**
     * load balance rule为round robin的requester
     */
    public static LoadBalanceRsocketRequester roundRobin(String serviceId,
                                                         Flux<Collection<String>> urisFactory,
                                                         RSocketRequesterSupport requesterSupport) {
        return new LoadBalanceRsocketRequester(serviceId, new RoundRobinSelector(), urisFactory, requesterSupport);
    }

    public LoadBalanceRsocketRequester(String serviceId,
                                       Selector selector,
                                       Flux<Collection<String>> urisFactory,
                                       RSocketRequesterSupport requesterSupport) {
        this.serviceId = serviceId;
        this.selector = selector;
        this.requesterSupport = requesterSupport;
        if (ServiceLocator.gsv(Symbols.BROKER).equals(serviceId) ||
                !RSocketServiceRegistry.exposedServices().isEmpty()) {
            //broker 即 provider
            this.isServiceProvider = true;
        }
        urisFactory.subscribe(this::refreshRSockets);
        //health check composite metadata
        //这里没有设置MessageMimeTypeMetadata, 是因为缺省的情况使用rsocket connector设置的dataMimeType来encode,
        //因为双端默认都是使用RSocketMimeType.defaultEncodingType进行数据序列化和反序列化, 所以没必要设置了, 还可以节省内存占用和socket bytes
        RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(
                GSVRoutingMetadata.of(null, HealthCheck.class.getCanonicalName(), "check", null));
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
                .publishOn(REFRESH_SCHEDULER)
                .subscribe(tupleRSockets -> {
                    if (tupleRSockets.isEmpty()) {
                        return;
                    }
                    Map<String, RSocket> newActiveRSockets = new HashMap<>();
                    for (Tuple2<String, RSocket> tuple : tupleRSockets) {
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
                        onRSocketConnected(entry.getKey(), entry.getValue());
                    }
                });
    }

    /**
     * 根据{@link LoadBalanceRsocketRequester#selector}选择一个有效的RSocket
     */
    private RSocket next() {
        if (activeRSockets.isEmpty()) {
            /**
             * TODO 解决延迟问题
             * {@link RSocketServiceRequester} 构建后快速调用service reference时, 会存在connection还未建立问题
             */
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                //do nothing
            }
        }
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
                    onRSocketClosed(next, error);
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
                    onRSocketClosed(next, error);
                    return fireAndForget(payload);
                });
    }

    @Override
    public Mono<Void> fireCloudEvent(CloudEventData<?> cloudEvent) {
        if (isDisposed()) {
            return (Mono<Void>) disposedMono();
        }
        try {
            Payload payload = CloudEventSupport.cloudEvent2Payload(cloudEvent);
            return metadataPush(payload);
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    @Override
    public Mono<Void> fireCloudEvent(String cloudEventJson) {
        if (isDisposed()) {
            return (Mono<Void>) disposedMono();
        }
        try {
            Payload payload = CloudEventSupport.cloudEvent2Payload(cloudEventJson);
            return metadataPush(payload);
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public Mono<Void> broadcastCloudEvent(CloudEventData<?> cloudEvent) {
        if (isDisposed()) {
            return (Mono<Void>) disposedMono();
        }
        try {
            return Flux.fromIterable(activeRSockets.values())
                    .flatMap(rsocket -> rsocket.metadataPush(CloudEventSupport.cloudEvent2Payload(cloudEvent)))
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
                    onRSocketClosed(next, error);
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
                    onRSocketClosed(next, error);
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
            rsocket.dispose();
        }
        activeRSockets.clear();
    }

    @Override
    public void refreshUnhealthyUris() {
        if (isDisposed()) {
            return;
        }
        for (String unhealthyUri : unhealthyUris) {
            tryToReconnect(unhealthyUri);
        }
    }

    private void onRSocketClosed(RSocket rsocket, Throwable cause) {
        for (Map.Entry<String, RSocket> entry : activeRSockets.entrySet()) {
            if (entry.getValue() == rsocket) {
                onRSocketClosed(entry.getKey(), entry.getValue(), cause);
            }
        }
    }

    /**
     * 连接closed逻辑处理
     */
    private void onRSocketClosed(String rsocketUri, RSocket rsocket, Throwable cause) {
        if (this.lastRefreshRSocketUris.contains(rsocketUri)) {
            this.unhealthyUris.add(rsocketUri);
            if (activeRSockets.containsKey(rsocketUri)) {
                Map<String, RSocket> activeRSockets = new HashMap<>(getActiveRSockets());
                activeRSockets.remove(rsocketUri);
                this.activeRSockets = activeRSockets;
                if (Objects.nonNull(cause)) {
                    log.error(String.format("connection '%s' closed, cause by", rsocketUri), cause);
                } else {
                    log.info(String.format("connection '%s' closed", rsocketUri));
                }
                tryToReconnect(rsocketUri, cause);
            }
        }
        if (!rsocket.isDisposed()) {
            rsocket.dispose();
        }
    }

    /**
     * 重连成功后, 刷新数据, 并注册暴露的服务
     */
    @SuppressWarnings("ConstantConditions")
    private void onRSocketReconnected(String rsocketUri, RSocket rsocket) {
        Map<String, RSocket> activeRSockets = new HashMap<>(getActiveRSockets());
        activeRSockets.put(rsocketUri, rsocket);
        this.activeRSockets = activeRSockets;
        this.unhealthyUris.remove(rsocketUri);
        onRSocketConnected(rsocketUri, rsocket);

        CloudEventData<RSocketServicesExposedEvent> cloudEvent = RSocketServiceRegistry.servicesExposedEvent();
        if (cloudEvent != null) {
            Payload payload = CloudEventSupport.cloudEvent2Payload(cloudEvent);
            rsocket.metadataPush(payload).subscribe();
        }

        log.info("requester reconnect '{}'", rsocketUri);
    }

    /**
     * 连接成功后, 对{@link RSocket}的处理
     */
    private void onRSocketConnected(String rsocketUri, RSocket rsocket) {
        rsocket.onClose()
                .publishOn(REFRESH_SCHEDULER)
                .doOnError(error -> {
                    if (CONNECTION_ERROR_PREDICATE.test(error)) {
                        //connection closed
                        onRSocketClosed(rsocketUri, rsocket, error);
                    }
                })
                .doOnSuccess(aVoid -> onRSocketClosed(rsocketUri, rsocket, null))
                .subscribe();
    }

    /**
     * 一分钟内每5s尝试重连
     */
    private void tryToReconnect(String rsocketUri, Throwable error) {
        //try to reconnect every 5 seconds in 1 minute if connection error
        if (Objects.isNull(error) || CONNECTION_ERROR_PREDICATE.test(error)) {
            //channel正常关闭 | 特定异常关闭
            tryToReconnect(rsocketUri);
        }
    }

    /**
     * 一分钟内每5s尝试重连
     */
    private void tryToReconnect(String rsocketUri) {
        if (!this.lastRefreshRSocketUris.contains(rsocketUri)) {
            //如果不是有效rsocket uri, 则不管
            return;
        }

        Flux.range(1, RETRY_COUNT)
                .delayElements(Duration.ofSeconds(5))
                .filter(id -> activeRSockets.isEmpty() || !activeRSockets.containsKey(rsocketUri))
                .subscribe(count -> {
                    if (LoadBalanceRsocketRequester.this.isDisposed()) {
                        return;
                    }
                    connect(rsocketUri)
                            .flatMap(rsocket -> healthCheck(rsocket, rsocketUri).map(payload -> rsocket))
                            .publishOn(REFRESH_SCHEDULER)
                            .doOnError(e -> {
                                log.error(String.format("reconnect '%s' error %d times", rsocketUri, count), e);
                                unhealthyUris.add(rsocketUri);
                            })
                            .subscribe(rsocket -> onRSocketReconnected(rsocketUri, rsocket));
                });
    }

    /**
     * build connect
     */
    private Mono<RSocket> connect(String uri) {
        if (isDisposed()) {
            return Mono.error(new IllegalStateException("requester is disposed"));
        }

        try {
            //requesterInterceptors
            RSocketConnector rsocketConnector = RSocketConnector.create();
            for (RSocketInterceptor requestInterceptor : requesterSupport.requesterInterceptors()) {
                rsocketConnector.interceptors(interceptorRegistry -> interceptorRegistry.forRequester(requestInterceptor));
            }
            //responderInterceptors
            for (RSocketInterceptor responderInterceptor : requesterSupport.responderInterceptors()) {
                rsocketConnector.interceptors(interceptorRegistry -> interceptorRegistry.forResponder(responderInterceptor));
            }
            Payload payload = requesterSupport.setupPayload().get();
            return rsocketConnector
                    .setupPayload(payload)
                    //metadata编码类型
                    .metadataMimeType(RSocketMimeType.COMPOSITE_METADATA.getType())
                    //setup data编码类型, remote默认的编码类型, 之所以使用json, 因为其平台无关性
                    .dataMimeType(RSocketMimeType.defaultEncodingType().getType())
                    .acceptor((setup, sendingSocket) -> requesterSupport.socketAcceptor().accept(setup, sendingSocket).doOnNext(responder -> {
                        //设置remote 推过来的cloud event source
                        if (responder instanceof RequestHandlerSupport) {
                            String sourcing = "upstream:";
                            if (this.serviceId.equals(Symbols.BROKER)) {
                                sourcing = sourcing + "broker:*";
                            } else {
                                sourcing = sourcing + ":" + serviceId;
                            }
                            ((RequestHandlerSupport) responder).setCloudEventSource(sourcing);
                        }
                    }))
                    //zero copy
                    .payloadDecoder(PayloadDecoder.ZERO_COPY)
                    .connect(UriTransportRegistry.INSTANCE.client(uri));
        } catch (Exception e) {
            log.error(String.format("connect '%s' error", uri), e);
            return Mono.error(new ConnectionErrorException(uri));
        }
    }

    /**
     * 每{@link LoadBalanceRsocketRequester#HEALTH_CHECK_INTERVAL_SECONDS}秒检查connection是否连接
     */
    private void startHealthCheck() {
        this.lastHealthCheckTimestamp = System.currentTimeMillis();
        healthCheckDisposable = Flux.interval(Duration.ofSeconds(HEALTH_CHECK_INTERVAL_SECONDS))
                .flatMap(timestamp -> {
                    this.lastHealthCheckTimestamp = System.currentTimeMillis();
                    return Flux.fromIterable(activeRSockets.entrySet());
                })
                .subscribe(entry ->
                        healthCheck(entry.getValue(), entry.getKey())
                                .doOnError(error -> {
                                    if (CONNECTION_ERROR_PREDICATE.test(error)) {
                                        //connection closed
                                        onRSocketClosed(entry.getKey(), entry.getValue(), error);
                                    }
                                }).subscribe());
    }

    /**
     * 每{@link LoadBalanceRsocketRequester#UNHEALTH_URIS_RECONNECT_MINS}分钟尝试重连
     */
    private void startUnhealthyUrisCheck() {
        unhealthUrisCheckDisposable = Flux.interval(Duration.ofMinutes(UNHEALTH_URIS_RECONNECT_MINS))
                .filter(sequence -> !unhealthyUris.isEmpty())
                .subscribe(entry -> {
                    for (String unhealthyUri : unhealthyUris) {
                        if (!lastRefreshRSocketUris.contains(unhealthyUri)) {
                            continue;
                        }
                        if (!activeRSockets.containsKey(unhealthyUri)) {
                            connect(unhealthyUri)
                                    .flatMap(rsocket -> healthCheck(rsocket, unhealthyUri).map(payload -> rsocket))
                                    .publishOn(REFRESH_SCHEDULER)
                                    .subscribe(rsocket -> onRSocketReconnected(unhealthyUri, rsocket));
                        }
                    }
                });
    }

    /**
     * health check
     */
    @SuppressWarnings("ConstantConditions")
    private Mono<Boolean> healthCheck(RSocket rsocket, String url) {
        return rsocket.requestResponse(ByteBufPayload.create(Unpooled.EMPTY_BUFFER, healthCheckCompositeByteBuf.retainedDuplicate()))
                .timeout(Duration.ofSeconds(HEALTH_CHECK_INTERVAL_SECONDS))
                .handle((payload, sink) -> {
                    /**
                     * 以{@link RSocketMimeType#defaultEncodingType()}编码
                     */
                    int result = (int) Codecs.INSTANCE.decodeResult(RSocketMimeType.defaultEncodingType(), payload.data(), Integer.class);
                    if (result == HealthCheck.SERVING) {
                        sink.next(true);
                    } else {
                        sink.error(new Exception("upstream health check failed :" + url));
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