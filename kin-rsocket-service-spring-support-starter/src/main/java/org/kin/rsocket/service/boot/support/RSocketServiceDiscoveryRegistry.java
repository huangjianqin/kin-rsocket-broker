package org.kin.rsocket.service.boot.support;

import com.google.common.base.Preconditions;
import io.rsocket.loadbalance.LoadbalanceTarget;
import org.jctools.maps.NonBlockingHashMap;
import org.kin.framework.utils.CollectionUtils;
import org.kin.framework.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.scheduling.annotation.Scheduled;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * 缓存{@link ReactiveDiscoveryClient}发现的rsocket service实例信息, 用于{@link RSocketRequester}实现负载均衡
 *
 * @author huangjianqin
 * @date 2022/3/15
 */
public final class RSocketServiceDiscoveryRegistry {
    private static final Logger log = LoggerFactory.getLogger(RSocketServiceDiscoveryRegistry.class);

    /** 如果fail结果是FAIL_NON_SERIALIZED, 则重试 */
    private static final Sinks.EmitFailureHandler RETRY_NON_SERIALIZED = (signalType, emitResult) -> {
        //多线程emit可能会导致FAIL_NON_SERIALIZED, 故此重试emit, 如果subscriber写得不好, 可能会导致100% cpu, 一直retry
        return emitResult == Sinks.EmitResult.FAIL_NON_SERIALIZED;
    };

    /** spring reactive discovery client */
    private final ReactiveDiscoveryClient discoveryClient;
    /** 自定义{@link RSocketRequester.Builder}自定义额外逻辑 */
    @Nullable
    private final RSocketRequesterBuilderCustomizer requesterBuilderCustomizer;

    /** key -> app name, value -> 该app所有实例信息 */
    private final ConcurrentMap<String, Sinks.Many<List<RSocketServiceInstance>>> app2ServiceInstances = new NonBlockingHashMap<>();
    /** {@link #app2ServiceInstances}的副本, 用于外部访问当前的app实例信息 */
    private final ConcurrentMap<String, List<RSocketServiceInstance>> snapshots = new NonBlockingHashMap<>();
    /** 上次刷新毫秒数 */
    private volatile long lastRefreshTimeMs;
    /** 是否在刷新中 */
    private volatile boolean refreshing;

    public RSocketServiceDiscoveryRegistry(ReactiveDiscoveryClient discoveryClient) {
        this(discoveryClient, null);
    }

    public RSocketServiceDiscoveryRegistry(ReactiveDiscoveryClient discoveryClient, @Nullable RSocketRequesterBuilderCustomizer requesterBuilderCustomizer) {
        this.discoveryClient = discoveryClient;
        this.requesterBuilderCustomizer = requesterBuilderCustomizer;
    }

    /**
     * 每15s刷新一下rsocket服务实例信息
     */
    @Scheduled(initialDelay = 5000, fixedRate = 15000)
    public void refreshServers() {
        if (!refreshing) {
            refreshing = true;
            lastRefreshTimeMs = System.currentTimeMillis();
            try {
                if (snapshots.isEmpty()) {
                    //没有任何rsocket service consumer
                    return;
                }

                for (String appName : app2ServiceInstances.keySet()) {
                    //refresh
                    discoveryClient.getInstances(appName)
                            .mapNotNull(this::toRSocketServiceInstance)
                            .collectList().subscribe(serviceInstances -> {
                                List<RSocketServiceInstance> currentServiceInstances = snapshots.get(appName);
                                //存在差异
                                if (currentServiceInstances.size() != serviceInstances.size() || !currentServiceInstances.containsAll(serviceInstances)) {
                                    log.debug("refresh upstream rsocket service instance for app '{}', {}", appName, serviceInstances);
                                    //更新rsocket service实例信息
                                    updateRSocketServiceInstances(appName, serviceInstances);
                                }
                            });
                }
            } finally {
                refreshing = false;
            }
        }
    }

    /**
     * 更新缓存的rsocket service instance信息
     */
    private void updateRSocketServiceInstances(String appName, List<RSocketServiceInstance> serviceInstances) {
        if (!app2ServiceInstances.containsKey(appName)) {
            return;
        }

        this.app2ServiceInstances.get(appName).emitNext(serviceInstances, RETRY_NON_SERIALIZED);
        this.snapshots.put(appName, serviceInstances);
    }

    /**
     * 将服务发现的所有{@link RSocketServiceInstance}转换成{@link LoadbalanceTarget}
     */
    private Flux<List<LoadbalanceTarget>> getRSocketLoadBalanceTargetListFlux(String serviceName) {
        return getRSocketLoadBalanceTargetListFlux(toAppName(serviceName), serviceName);
    }

    /**
     * 将服务发现的所有{@link RSocketServiceInstance}转换成{@link LoadbalanceTarget}
     */
    private Flux<List<LoadbalanceTarget>> getRSocketLoadBalanceTargetListFlux(String appName, String serviceName) {
        if (StringUtils.isBlank(appName)) {
            appName = toAppName(serviceName);
        }
        if (app2ServiceInstances.containsKey(appName)) {
            //使用缓存的
            return app2ServiceInstances.get(appName)
                    .asFlux()
                    .map(this::toLoadBalanceTarget);
        }

        //重新拉
        app2ServiceInstances.put(appName, Sinks.many().replay().latest());
        String finalAppName = appName;
        return Flux.from(discoveryClient.getInstances(appName)
                        .mapNotNull(this::toRSocketServiceInstance)
                        .collectList()
                        .doOnNext(serviceInstances -> updateRSocketServiceInstances(finalAppName, serviceInstances)))
                .thenMany(app2ServiceInstances.get(appName).asFlux().map(this::toLoadBalanceTarget));
    }

    /**
     * 转换成{@link LoadbalanceTarget}list
     */
    private List<LoadbalanceTarget> toLoadBalanceTarget(List<RSocketServiceInstance> serviceInstances) {
        return serviceInstances.stream()
                .map(serviceInstance -> LoadbalanceTarget.from(serviceInstance.getHost() + serviceInstance.getPort(), serviceInstance.toClientTransport()))
                .collect(Collectors.toList());
    }

    /**
     * 获取rsocket service的app name
     */
    private String toAppName(String serviceName) {
        String appName = serviceName.replaceAll("\\.", "-");
        if (appName.contains(":")) {
            // service name 前缀带有 app name, 则直接取.
            // 比如broker模式, service挂靠在指定broker下, 则可以将该service name设置为{broker app name}:{real service name}
            return appName.substring(0, appName.indexOf(":"));
        }

        //默认规定, 假设rsocket service interface定义为a.b.c.S, 则a-b-c为该rsocket service的app name
        //取最后一个'-'后面的字符串
        String mayBeService = appName.substring(appName.lastIndexOf("-") + 1);
        if (Character.isUpperCase(mayBeService.toCharArray()[0])) {
            //首字母大写, 则是service类名, 截断最后一个'-'后面的字符串
            appName = appName.substring(0, appName.lastIndexOf("-"));
        }
        return appName;
    }

    /**
     * 将{@link ServiceInstance}转换成{@link RSocketServiceInstance}
     *
     * @return 如果metadata没有相关rsocket service信息, 则认为该service不是rsocket service
     */
    @Nullable
    private RSocketServiceInstance toRSocketServiceInstance(ServiceInstance serviceInstance) {
        //rsocket schema和对应port, 支持设置多个, 但这里仅仅取第一个
        String rsocketSchemaPorts = serviceInstance.getMetadata().getOrDefault("rsocketSchemaPorts", "tcp:10001");
        String[] strings = rsocketSchemaPorts.split(";");
        if (CollectionUtils.isNonEmpty(strings)) {
            //只取第一
            String[] schemaPortStrs = strings[0].split(":");

            RSocketServiceInstance rsocketServiceInstance = new RSocketServiceInstance(serviceInstance.getHost(), Integer.parseInt(schemaPortStrs[1]), schemaPortStrs[0]);
            if (rsocketServiceInstance.isWebSocket()) {
                //如果使用了websocket, spring mapping path默认为/rsocket
                rsocketServiceInstance.updateWsPath(serviceInstance.getMetadata().getOrDefault("rsocketWsPath", "/rsocket"));
            }

            return rsocketServiceInstance;
        }
        // 该服务实例没有rsocket service相关的metadata
        return null;
    }

    //---------------------------------------------api---------------------------------------------

    /**
     * @param builder prototype类型, 可以放心为每个requester设置不同setup payload
     */
    public RSocketRequester createLoadBalanceRSocketRequester(String appName, String serviceName, RSocketRequester.Builder builder, LoadbalanceStrategyFactory loadbalanceStrategyFactory) {
        if (Objects.nonNull(requesterBuilderCustomizer)) {
            requesterBuilderCustomizer.customize(builder, appName, serviceName);
        }
        return builder.transports(this.getRSocketLoadBalanceTargetListFlux(appName, serviceName), loadbalanceStrategyFactory.strategy());
    }

    public RSocketRequester createLoadBalanceRSocketRequester(String appName, String serviceName, RSocketRequester.Builder builder) {
        return createLoadBalanceRSocketRequester(appName, serviceName, builder, LoadbalanceStrategyFactory.WEIGHTED);
    }

    public RSocketRequester createLoadBalanceRSocketRequester(String serviceName, RSocketRequester.Builder builder) {
        return createLoadBalanceRSocketRequester(toAppName(serviceName), serviceName, builder);
    }

    public RSocketRequester createLoadBalanceRSocketRequester(String appName, Class<?> serviceInterface, RSocketRequester.Builder builder) {
        Preconditions.checkArgument(serviceInterface.isInterface());
        return createLoadBalanceRSocketRequester(appName, serviceInterface.getName(), builder);
    }

    public RSocketRequester createLoadBalanceRSocketRequester(Class<?> serviceInterface, RSocketRequester.Builder builder) {
        Preconditions.checkArgument(serviceInterface.isInterface());
        return createLoadBalanceRSocketRequester(serviceInterface.getName(), builder);
    }

    //getter
    public Map<String, List<RSocketServiceInstance>> getServiceInstances() {
        return snapshots;
    }

    public long getLastRefreshTimeMs() {
        return lastRefreshTimeMs;
    }
}
