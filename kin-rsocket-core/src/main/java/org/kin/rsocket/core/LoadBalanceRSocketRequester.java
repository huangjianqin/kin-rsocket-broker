package org.kin.rsocket.core;

import io.cloudevents.CloudEvent;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.core.RSocketConnector;
import io.rsocket.exceptions.ConnectionErrorException;
import io.rsocket.frame.decoder.PayloadDecoder;
import io.rsocket.loadbalance.WeightedStatsRequestInterceptor;
import io.rsocket.plugins.DuplexConnectionInterceptor;
import io.rsocket.plugins.RSocketInterceptor;
import io.rsocket.plugins.RequestInterceptor;
import io.rsocket.util.ByteBufPayload;
import org.jctools.maps.NonBlockingHashSet;
import org.kin.framework.utils.CollectionUtils;
import org.kin.framework.utils.ExtensionLoader;
import org.kin.framework.utils.SysUtils;
import org.kin.rsocket.core.codec.ObjectCodecs;
import org.kin.rsocket.core.event.CloudEventRSocket;
import org.kin.rsocket.core.event.CloudEventSupport;
import org.kin.rsocket.core.health.HealthCheck;
import org.kin.rsocket.core.metadata.GSVRoutingMetadata;
import org.kin.rsocket.core.metadata.RSocketCompositeMetadata;
import org.kin.rsocket.core.transport.UriTransportRegistry;
import org.kin.rsocket.core.upstream.loadbalance.UpstreamLoadBalance;
import org.kin.rsocket.core.upstream.loadbalance.WeightedStatsUpstreamLoadBalance;
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

import javax.annotation.Nonnull;
import java.net.ConnectException;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * @author huangjianqin
 * @date 2021/3/27
 */
@SuppressWarnings("unchecked")
public class LoadBalanceRSocketRequester extends AbstractRSocket implements CloudEventRSocket, RequesterRSocket {
    private static final Logger log = LoggerFactory.getLogger(LoadBalanceRSocketRequester.class);
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
    /** requester通用scheduler, 主要负责处理health check和reconnect */
    private static final Scheduler COMMON_SCHEDULER = Schedulers.newParallel("LoadBalanceRequester-Common", SysUtils.CPU_NUM, true);

    /** load balance rule */
    private final UpstreamLoadBalance loadBalance;
    /** service id */
    private final String serviceGsv;
    /** 上一次refresh uri 时间 */
    private volatile long lastRefreshTimestamp;
    /** 上次刷新的rsocket uris */
    private volatile Collection<String> lastRefreshRSocketUris = Collections.emptyList();
    /** 有效的rsocket连接, 一写, 多读, copy-on-write */
    private volatile Map<String, RSocket> activeRSockets = Collections.emptyMap();
    /** unhealthy uris */
    private final Set<String> unhealthyUris = new NonBlockingHashSet<>();
    /** 上一次health check时间 */
    private volatile long lastHealthCheckTimestamp;
    /** requester配置 */
    private final RSocketRequesterSupport requesterSupport;
    /** upstream uri刷新逻辑单线程处理 */
    private final Scheduler refreshScheduler = Schedulers.newSingle("LoadBalanceRequester-Refresh", true);
    /** health check 元数据bytes, 避免多次创建bytes */
    private final ByteBuf healthCheckCompositeByteBuf;
    /** 是否是service provider */
    private boolean isServiceProvider = false;
    /** health check disposable */
    private Disposable healthCheckDisposable;
    /** unhealth uris check disposable */
    private Disposable unhealthUrisCheckDisposable;
    /** 首次创建时, 用于等待连接建立成功, 然后释放掉 */
    private volatile CountDownLatch latch = new CountDownLatch(1);

    public LoadBalanceRSocketRequester(String serviceGsv,
                                       Flux<Collection<String>> urisFactory,
                                       RSocketRequesterSupport requesterSupport) {
        this(serviceGsv, null, urisFactory, requesterSupport);
    }

    /**
     * @param loadBalanceStrategy 负载均衡策略名
     */
    public LoadBalanceRSocketRequester(String serviceGsv,
                                       String loadBalanceStrategy,
                                       Flux<Collection<String>> urisFactory,
                                       RSocketRequesterSupport requesterSupport) {
        this.serviceGsv = serviceGsv;
        this.loadBalance = tryLoadUpstreamLoadBalance(loadBalanceStrategy);
        this.requesterSupport = requesterSupport;
        if (ServiceLocator.gsv(Symbols.BROKER).equals(serviceGsv) ||
                !LocalRSocketServiceRegistry.exposedServices().isEmpty()) {
            //broker 即 provider
            this.isServiceProvider = true;
        }
        urisFactory.subscribe(this::refreshRSockets);
        //health check composite metadata
        //这里没有设置MessageMimeTypeMetadata, 是因为缺省的情况使用rsocket connector设置的dataMimeType来encode,
        //因为双端默认都是使用RSocketMimeType.defaultEncodingType进行数据序列化和反序列化, 所以没必要设置了, 还可以节省内存占用和socket bytes
        RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.from(
                GSVRoutingMetadata.from(null, HealthCheck.class.getName(), "check", null));
        ByteBuf compositeMetadataContent = compositeMetadata.getContent();
        this.healthCheckCompositeByteBuf = Unpooled.copiedBuffer(compositeMetadataContent);
        ReferenceCountUtil.safeRelease(compositeMetadataContent);

        //start health check timer
        startHealthCheck();
        //start check and reconnect unhealthy uris
        startUnhealthyUrisCheck();
    }

    /**
     * 通过kin-spi机制加载, 如果没有, 则默认round-robin
     */
    private UpstreamLoadBalance tryLoadUpstreamLoadBalance(String loadBalanceStrategy) {
        //默认round-robin
        return ExtensionLoader.getExtensionOrDefault(UpstreamLoadBalance.class, loadBalanceStrategy);
    }

    /** 刷新RSocket实例 */
    private void refreshRSockets(Collection<String> rsocketUris) {
        //no changes
        if (CollectionUtils.isSame(this.lastRefreshRSocketUris, rsocketUris)) {
            return;
        }
        this.lastRefreshTimestamp = System.currentTimeMillis();
        this.lastRefreshRSocketUris = rsocketUris;
        Flux.fromIterable(rsocketUris)
                .publishOn(refreshScheduler)
                //多线程connect remote
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
                //单线程更新有效remote connection
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
                                .publishOn(COMMON_SCHEDULER)
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

                    if (Objects.nonNull(latch)) {
                        latch.countDown();
                        //释放掉
                        latch = null;
                    }
                });
    }

    /**
     * 等待首次连接建立成功
     */
    private void awaitFirstConnect() {
        if (Objects.nonNull(latch)) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                //ignore
            }
        }
    }

    /**
     * 根据{@link LoadBalanceRSocketRequester#loadBalance}选择一个有效的RSocket
     */
    private Mono<RSocket> next(ByteBuf paramBytes) {
        return Mono.fromSupplier(() -> {
            awaitFirstConnect();
            RSocket selected = loadBalance.select(serviceGsv.hashCode(), paramBytes, new ArrayList<>(activeRSockets.values()));
            if (Objects.isNull(selected)) {
                throw new NoAvailableConnectionException(serviceGsv);
            }
            return selected;
        });
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

    @Nonnull
    @Override
    public Mono<Payload> requestResponse(@Nonnull Payload payload) {
        if (isDisposed()) {
            return (Mono<Payload>) disposedMono();
        }
        return next(payload.data())
                .doOnError(ex -> ReferenceCountUtil.safeRelease(payload))
                .flatMap(rsocket -> rsocket.requestResponse(payload)
                        .onErrorResume(CONNECTION_ERROR_PREDICATE, error -> {
                            onRSocketClosed(rsocket, error);
                            return requestResponse(payload);
                        }));

    }

    @Nonnull
    @Override
    public Mono<Void> fireAndForget(@Nonnull Payload payload) {
        if (isDisposed()) {
            return (Mono<Void>) disposedMono();
        }
        return next(payload.data())
                .doOnError(ex -> ReferenceCountUtil.safeRelease(payload))
                .flatMap(rsocket -> rsocket.fireAndForget(payload)
                        .onErrorResume(CONNECTION_ERROR_PREDICATE, error -> {
                            onRSocketClosed(rsocket, error);
                            return fireAndForget(payload);
                        }));
    }

    @Override
    public Mono<Void> fireCloudEvent(CloudEvent cloudEvent) {
        return broadcastCloudEvent(cloudEvent);
    }

    @Override
    public Mono<Void> fireCloudEvent(byte[] cloudEventBytes) {
        if (isDisposed()) {
            return (Mono<Void>) disposedMono();
        }
        try {
            Payload payload = CloudEventSupport.cloudEventBytes2Payload(cloudEventBytes);
            return metadataPush(payload);
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    @Override
    public Mono<Void> broadcastCloudEvent(CloudEvent cloudEvent) {
        if (isDisposed()) {
            return (Mono<Void>) disposedMono();
        }
        try {
            Payload payload = CloudEventSupport.cloudEvent2Payload(cloudEvent);
            return metadataPush(payload)
                    .doOnError(throwable -> {
                        log.error("Failed to fire event to all upstream nodes", throwable);
                        ReferenceCountUtil.safeRelease(payload);
                    })
                    .then();
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    @Nonnull
    @Override
    public Flux<Payload> requestStream(@Nonnull Payload payload) {
        if (isDisposed()) {
            return (Flux<Payload>) disposedFlux();
        }
        return next(payload.data())
                .doOnError(ex -> ReferenceCountUtil.safeRelease(payload))
                .flatMapMany(rsocket -> rsocket.requestStream(payload)
                        .onErrorResume(CONNECTION_ERROR_PREDICATE, error -> {
                            onRSocketClosed(rsocket, error);
                            return requestStream(payload);
                        }));
    }

    @Nonnull
    @Override
    public Flux<Payload> requestChannel(@Nonnull Publisher<Payload> payloads) {
        if (isDisposed()) {
            return (Flux<Payload>) disposedFlux();
        }

        return next(null)
                .doOnError(ex -> ReferenceCountUtil.safeRelease(payloads))
                .flatMapMany(rsocket -> rsocket.requestChannel(payloads)
                        .onErrorResume(CONNECTION_ERROR_PREDICATE, error -> {
                            onRSocketClosed(rsocket, error);
                            return requestChannel(payloads);
                        }));
    }

    @Nonnull
    @Override
    public Mono<Void> metadataPush(@Nonnull Payload payload) {
        if (isDisposed()) {
            return (Mono<Void>) disposedMono();
        }
        awaitFirstConnect();
        return Flux.fromIterable(activeRSockets.values()).flatMap(rsocket -> rsocket.metadataPush(payload)).then();
    }

    @Override
    public void dispose() {
        super.dispose();
        refreshScheduler.dispose();
        healthCheckDisposable.dispose();
        unhealthUrisCheckDisposable.dispose();
        for (RSocket rsocket : activeRSockets.values()) {
            rsocket.dispose();
        }
        activeRSockets.clear();
    }

    @Override
    public boolean isDisposed() {
        return super.isDisposed();
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
    private void onRSocketReconnected(String rsocketUri, RSocket rsocket) {
        Map<String, RSocket> activeRSockets = new HashMap<>(getActiveRSockets());
        activeRSockets.put(rsocketUri, rsocket);
        this.activeRSockets = activeRSockets;
        this.unhealthyUris.remove(rsocketUri);
        onRSocketConnected(rsocketUri, rsocket);

        CloudEvent cloudEvent = LocalRSocketServiceRegistry.servicesExposedCloudEvent();
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
                .publishOn(COMMON_SCHEDULER)
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
                .delayElements(Duration.ofSeconds(5), COMMON_SCHEDULER)
                .filter(id -> activeRSockets.isEmpty() || !activeRSockets.containsKey(rsocketUri))
                .subscribe(count -> {
                    if (LoadBalanceRSocketRequester.this.isDisposed()) {
                        return;
                    }
                    connect(rsocketUri)
                            .flatMap(rsocket -> healthCheck(rsocket, rsocketUri).map(payload -> rsocket))
                            .publishOn(COMMON_SCHEDULER)
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
            for (RSocketInterceptor interceptor : requesterSupport.requesterInterceptors()) {
                rsocketConnector.interceptors(interceptorRegistry -> interceptorRegistry.forRequester(interceptor));
            }

            if (loadBalance instanceof WeightedStatsUpstreamLoadBalance) {
                //需要注册requester interceptor来收集rsocket请求的统计信息
                rsocketConnector.interceptors(interceptorRegistry ->
                        interceptorRegistry.forRequestsInRequester((Function<RSocket, ? extends RequestInterceptor>)
                                rsocket -> {
                                    WeightedStatsUpstreamLoadBalance loadBalance = (WeightedStatsUpstreamLoadBalance) this.loadBalance;
                                    WeightedStatsRequestInterceptor interceptor = new WeightedStatsRequestInterceptor() {
                                        @Override
                                        public void dispose() {
                                            loadBalance.remove(rsocket);
                                        }
                                    };
                                    loadBalance.put(rsocket, interceptor);

                                    return interceptor;
                                }));
            }

            //responderInterceptors
            for (RSocketInterceptor interceptor : requesterSupport.responderInterceptors()) {
                rsocketConnector.interceptors(interceptorRegistry -> interceptorRegistry.forResponder(interceptor));
            }

            //connectionInterceptors
            for (DuplexConnectionInterceptor interceptor : requesterSupport.connectionInterceptors()) {
                rsocketConnector.interceptors(interceptorRegistry -> interceptorRegistry.forConnection(interceptor));
            }

            //requesterRequestInterceptors
            for (Function<RSocket, ? extends RequestInterceptor> interceptor : requesterSupport.requesterRequestInterceptors()) {
                rsocketConnector.interceptors(interceptorRegistry -> interceptorRegistry.forRequestsInRequester(interceptor));
            }

            //responderRequestInterceptors
            for (Function<RSocket, ? extends RequestInterceptor> interceptor : requesterSupport.responderRequestInterceptors()) {
                rsocketConnector.interceptors(interceptorRegistry -> interceptorRegistry.forRequestsInResponder(interceptor));
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
                        if (responder instanceof RSocketRequestHandlerSupport) {
                            String sourcing = "upstream:";
                            if (this.serviceGsv.equals(Symbols.BROKER)) {
                                sourcing = sourcing + "broker:*";
                            } else {
                                sourcing = sourcing + ":" + serviceGsv;
                            }
                            ((RSocketRequestHandlerSupport) responder).setCloudEventSource(sourcing);
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
     * 每{@link LoadBalanceRSocketRequester#HEALTH_CHECK_INTERVAL_SECONDS}秒检查connection是否连接
     */
    private void startHealthCheck() {
        this.lastHealthCheckTimestamp = System.currentTimeMillis();
        healthCheckDisposable = Flux.interval(Duration.ofSeconds(HEALTH_CHECK_INTERVAL_SECONDS), COMMON_SCHEDULER)
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
     * 每{@link LoadBalanceRSocketRequester#UNHEALTH_URIS_RECONNECT_MINS}分钟尝试重连
     */
    private void startUnhealthyUrisCheck() {
        unhealthUrisCheckDisposable = Flux.interval(Duration.ofMinutes(UNHEALTH_URIS_RECONNECT_MINS), COMMON_SCHEDULER)
                .filter(sequence -> !unhealthyUris.isEmpty())
                .subscribe(entry -> {
                    for (String unhealthyUri : unhealthyUris) {
                        if (!lastRefreshRSocketUris.contains(unhealthyUri)) {
                            continue;
                        }

                        if (activeRSockets.containsKey(unhealthyUri)) {
                            continue;
                        }

                        connect(unhealthyUri)
                                .flatMap(rsocket -> healthCheck(rsocket, unhealthyUri).map(payload -> rsocket))
                                .publishOn(COMMON_SCHEDULER)
                                .subscribe(rsocket -> onRSocketReconnected(unhealthyUri, rsocket),
                                        error -> tryToReconnect(unhealthyUri, error));
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
                    try {
                        /*
                         * 以{@link RSocketMimeType#defaultEncodingType()}编码
                         */
                        int result = (int) ObjectCodecs.INSTANCE.decodeResult(RSocketMimeType.defaultEncodingType(), payload.data(), Integer.class);
                        if (result == HealthCheck.SERVING) {
                            sink.next(true);
                        } else {
                            sink.error(new Exception("upstream health check failed :" + url));
                        }
                    } finally {
                        ReferenceCountUtil.safeRelease(payload);
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