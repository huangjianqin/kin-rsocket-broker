package org.kin.rsocket.service;

import brave.Tracer;
import org.kin.rsocket.core.*;
import org.kin.rsocket.core.domain.RSocketServiceInfo;
import org.kin.rsocket.core.event.*;
import org.kin.rsocket.core.health.HealthCheck;
import org.kin.rsocket.service.event.ServiceInstanceChangedEventConsumer;
import org.kin.rsocket.service.event.UpstreamClusterChangedEventConsumer;
import org.kin.rsocket.service.health.BrokerHealthCheckReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * all in one
 * broker requester, 包含绑定rsocket server, 连接broker, 注册服务, 注销服务, 添加cloud event consumer和broker连接管理等功能
 *
 * @author huangjianqin
 * @date 2021/3/28
 */
public final class RSocketServiceRequester implements UpstreamClusterManager {
    private static final Logger log = LoggerFactory.getLogger(RSocketServiceRequester.class);

    //状态-初始
    private static final int STATE_INIT = 0;
    //状态-已启动
    private static final int STATE_START = 1;
    //状态-已closed
    private static final int STATE_TERMINATED = 2;

    /** app name */
    private final String appName;
    /** 配置 */
    private final RSocketServiceProperties rsocketServiceProperties;
    /** zipkin */
    private final Tracer tracer;
    /** upstream cluster manager */
    private final UpstreamClusterManagerImpl upstreamClusterManager;
    /** requester连接配置 */
    private final RSocketRequesterSupportImpl requesterSupport;
    /** rsocket binder */
    private final RSocketBinder binder;
    /** 状态 */
    private AtomicInteger state = new AtomicInteger(STATE_INIT);

    private RSocketServiceRequester(String appName,
                                    RSocketServiceProperties rsocketServiceProperties,
                                    Tracer tracer,
                                    List<RSocketBinderCustomizer> binderCustomizers,
                                    List<RSocketRequesterSupportCustomizer> requesterSupportCustomizers,
                                    HealthCheck customHealthCheck) {
        this.appName = appName;
        this.rsocketServiceProperties = rsocketServiceProperties;
        this.tracer = tracer;

        //1. create binder
        int rsocketBindPort = rsocketServiceProperties.getPort();
        if (rsocketBindPort > 0) {
            RSocketBinder.Builder binderBuilder = RSocketBinder.builder();
            binderBuilder.acceptor((setupPayload, requester) -> Mono.just(new RSocketResponderHandler(requester, setupPayload, tracer)));
            binderBuilder.listen(rsocketServiceProperties.getSchema(), rsocketBindPort);
            binderCustomizers.forEach((customizer) -> customizer.customize(binderBuilder));
            binder = binderBuilder.build();
        } else {
            binder = null;
        }

        //2.1 create requester support
        requesterSupport = new RSocketRequesterSupportImpl(rsocketServiceProperties, appName, tracer);
        //2.2 custom requester support
        requesterSupportCustomizers.forEach((customizer) -> customizer.customize(requesterSupport));
        //2.3 init upstream manager
        upstreamClusterManager = new UpstreamClusterManagerImpl(requesterSupport, rsocketServiceProperties.getLoadBalance());

        //3. register health check
        if (Objects.isNull(customHealthCheck)) {
            customHealthCheck = new BrokerHealthCheckReference(upstreamClusterManager);
        }
        registerService(HealthCheck.class, customHealthCheck);

        //4. add internal cloud event consumer
        CloudEventConsumers.INSTANCE.addConsumer(new UpstreamClusterChangedEventConsumer(upstreamClusterManager));
        CloudEventConsumers.INSTANCE.addConsumer(new ServiceInstanceChangedEventConsumer(upstreamClusterManager));
    }

    /**
     * 初始化requester
     */
    public void init() {
        if (!state.compareAndSet(STATE_INIT, STATE_START)) {
            return;
        }

        //1. bind
        if (Objects.nonNull(binder)) {
            binder.bind();
        }

        //2. connect
        add(rsocketServiceProperties);
    }

    /**
     * 某些接口调用需要检查是否已启动
     */
    private void checkStart() {
        if (state.get() != STATE_START) {
            throw new IllegalStateException("RSocketServiceRequester doesn't init");
        }
    }

    /**
     * 注册service
     */
    public RSocketServiceRequester registerService(Class<?> serviceInterface, Object provider) {
        return registerService(rsocketServiceProperties.getGroup(), rsocketServiceProperties.getVersion(), serviceInterface, provider);
    }

    /**
     * 注册service
     */
    public RSocketServiceRequester registerService(String group, String version, Class<?> serviceInterface, Object provider, String... tags) {
        LocalRSocketServiceRegistry.INSTANCE.addProvider(group, version, serviceInterface, provider, tags);
        return this;
    }

    /**
     * 注册service
     */
    public RSocketServiceRequester registerService(String service, Class<?> serviceInterface, Object provider, String... tags) {
        return registerService(rsocketServiceProperties.getGroup(), service, rsocketServiceProperties.version, serviceInterface, provider, tags);
    }

    /**
     * 注册service
     */
    public RSocketServiceRequester registerService(String group, String service, String version, Class<?> serviceInterface, Object provider, String... tags) {
        LocalRSocketServiceRegistry.INSTANCE.addProvider(group, service, version, serviceInterface, provider, tags);
        return this;
    }

    /**
     * 注册service
     * 供cloud function使用, 因为其无法获取真实的service信息, 所以, 只能在外部从spring function registry中尽量提取service信息, 然后进行注册
     */
    public RSocketServiceRequester registerService(String handler, Object provider, ReactiveMethodInvoker invoker, RSocketServiceInfo serviceInfo) {
        LocalRSocketServiceRegistry.INSTANCE.addProvider(handler, provider, invoker, serviceInfo);
        return this;
    }

    /**
     * 注册并发布service
     */
    public RSocketServiceRequester registerAndPubService(Class<?> serviceInterface, Object provider) {
        return registerAndPubService(rsocketServiceProperties.getGroup(), rsocketServiceProperties.getVersion(), serviceInterface, provider);
    }

    /**
     * 注册并发布service
     */
    public RSocketServiceRequester registerAndPubService(String group, String version, Class<?> serviceInterface, Object provider) {
        registerService(group, version, serviceInterface, provider);
        publishService(group, serviceInterface.getName(), version);
        return this;
    }

    /**
     * 注册并发布service
     */
    public RSocketServiceRequester registerAndPubService(String service, Class<?> serviceInterface, Object provider) {
        return registerAndPubService(rsocketServiceProperties.getGroup(), service, rsocketServiceProperties.getVersion(), serviceInterface, provider);
    }

    /**
     * 注册并发布service
     */
    public RSocketServiceRequester registerAndPubService(String group, String service, String version, Class<?> serviceInterface, Object provider) {
        registerService(group, service, version, serviceInterface, provider);
        publishService(group, service, version);
        return this;
    }

    /**
     * 注册并发布service
     * 供cloud function使用, 因为其无法获取真实的service信息, 所以, 只能在外部从spring function registry中尽量提取service信息, 然后进行注册
     */
    public RSocketServiceRequester registerAndPubService(String handler, Object provider, ReactiveMethodInvoker invoker, RSocketServiceInfo serviceInfo) {
        LocalRSocketServiceRegistry.INSTANCE.addProvider(handler, provider, invoker, serviceInfo);
        publishService(serviceInfo.getGroup(), serviceInfo.getService(), serviceInfo.getVersion());
        return this;
    }

    /**
     * 获取broker urls字符串, 以,分割
     */
    private String getBrokerUris() {
        return String.join(",", rsocketServiceProperties.getBrokers());
    }

    /**
     * 发布(暴露)服务
     */
    public void publishServices() {
        // service exposed
        CloudEventData<RSocketServicesExposedEvent> servicesExposedEventCloudEvent = LocalRSocketServiceRegistry.servicesExposedEvent();
        if (servicesExposedEventCloudEvent != null) {
            publishServices(servicesExposedEventCloudEvent);
        }
    }

    /**
     * 通知broker暴露新服务
     */
    private void publishServices(CloudEventData<RSocketServicesExposedEvent> servicesExposedEventCloudEvent) {
        UpstreamCluster broker = getBroker();
        if (Objects.isNull(broker)) {
            return;
        }

        broker.broadcastCloudEvent(servicesExposedEventCloudEvent)
                .doOnSuccess(aVoid -> {
                    //broker uris
                    String brokerUris = String.join(",", rsocketServiceProperties.getBrokers());
                    String exposedServiceIds = LocalRSocketServiceRegistry.exposedServices().stream().map(ServiceLocator::getGsv).collect(Collectors.joining(", "));
                    log.info(String.format("services(%s) published on Brokers(%s)!", exposedServiceIds, brokerUris));
                }).subscribe();
    }

    /**
     * 通知broker暴露新服务
     */
    private void publishService(String group, String service, String version) {
        //publish
        CloudEventData<RSocketServicesExposedEvent> cloudEvent = RSocketServicesExposedEvent.of(ServiceLocator.of(group, service, version));
        publishServices(cloudEvent);
    }

    /**
     * 下线服务
     */
    public void hideService(String service, Class<?> serviceInterface) {
        hideService(rsocketServiceProperties.getGroup(), service, rsocketServiceProperties.getVersion(), serviceInterface);
    }

    /**
     * 下线服务
     */
    public void hideService(String group, String service, String version, Class<?> serviceInterface) {
        UpstreamCluster broker = getBroker();
        if (Objects.isNull(broker)) {
            return;
        }
        ServiceLocator targetServiceLocator = ServiceLocator.of(group, service, version);
        CloudEventData<RSocketServicesHiddenEvent> cloudEvent = RSocketServicesHiddenEvent.of(Collections.singletonList(targetServiceLocator));
        broker.broadcastCloudEvent(cloudEvent)
                .doOnSuccess(unused -> {
                    //broker uris
                    String brokerUris = String.join(",", rsocketServiceProperties.getBrokers());

                    LocalRSocketServiceRegistry.INSTANCE.removeProvider(group, service, version, serviceInterface);
                    log.info(String.format("Services(%s) hide on Brokers(%s)!.", service, brokerUris));
                }).subscribe();
    }

    /**
     * 添加一个{@link CloudEventConsumer}
     */
    public void addConsumer(CloudEventConsumer consumer) {
        CloudEventConsumers.INSTANCE.addConsumers(consumer);
    }

    /**
     * 批量添加{@link CloudEventConsumer}
     */
    public void addConsumers(CloudEventConsumer... consumers) {
        CloudEventConsumers.INSTANCE.addConsumers(Arrays.asList(consumers));
    }

    /**
     * 批量添加{@link CloudEventConsumer}
     */
    public void addConsumers(Collection<CloudEventConsumer> consumers) {
        CloudEventConsumers.INSTANCE.addConsumers(consumers);
    }

    /**
     * 设置app状态为提供服务中, 并暴露所有已注册的服务
     */
    public void serving() {
        UpstreamCluster broker = getBroker();
        if (Objects.nonNull(broker)) {
            //notify broker app status update
            broker.broadcastCloudEvent(AppStatusEvent.serving(RSocketAppContext.ID).toCloudEvent())
                    .doOnSuccess(aVoid -> log.info(String.format("application connected with RSocket Brokers(%s) successfully", String.join(",", rsocketServiceProperties.getBrokers()))))
                    .subscribe();
            publishServices();
        }
    }

    @Override
    public void close() {
        if (!state.compareAndSet(STATE_START, STATE_TERMINATED)) {
            return;
        }

        upstreamClusterManager.close();
        if (Objects.nonNull(binder)) {
            binder.close();
        }
    }

    //--------------------------------------------------overwrite UpstreamClusterManager----------------------------------------------------------------------
    @Override
    public void add(String group, String service, String version, List<String> uris) {
        upstreamClusterManager.add(group, service, version, uris);
    }

    @Override
    public void add(RSocketServiceProperties rsocketServiceProperties) {
        upstreamClusterManager.add(rsocketServiceProperties);
    }

    @Override
    public Collection<UpstreamCluster> getAll() {
        checkStart();
        return upstreamClusterManager.getAll();
    }

    @Override
    public UpstreamCluster get(String serviceId) {
        checkStart();
        return upstreamClusterManager.get(serviceId);
    }

    @Override
    public UpstreamCluster getBroker() {
        checkStart();
        return upstreamClusterManager.getBroker();
    }

    @Override
    public void refresh(String serviceId, List<String> uris) {
        checkStart();
        upstreamClusterManager.refresh(serviceId, uris);
    }

    @Override
    public RSocketRequesterSupport getRequesterSupport() {
        return upstreamClusterManager.getRequesterSupport();
    }

    @Override
    public void remove(String serviceId) {
        checkStart();
        upstreamClusterManager.remove(serviceId);
    }

    @Override
    public void openP2p(String... gsvs) {
        upstreamClusterManager.openP2p(gsvs);
    }

    @Override
    public Set<String> getP2pServices() {
        return upstreamClusterManager.getP2pServices();
    }

    @Override
    public UpstreamCluster select(String serviceId) {
        checkStart();
        return upstreamClusterManager.select(serviceId);
    }
    //--------------------------------------------------overwrite UpstreamClusterManager----------------------------------------------------------------------

    //--------------------------------------------------内部类----------------------------------------------------------------------
    public static Builder builder(String appName, RSocketServiceProperties rsocketServiceProperties) {
        return new Builder(appName, rsocketServiceProperties);
    }

    /** builder **/
    public static class Builder {
        private final String appName;
        private final RSocketServiceProperties rsocketServiceProperties;
        private List<RSocketBinderCustomizer> binderCustomizers = Collections.emptyList();
        private List<RSocketRequesterSupportCustomizer> requesterSupportCustomizers = Collections.emptyList();
        private HealthCheck customHealthCheck;
        /** zipkin */
        private Tracer tracer;

        public Builder(String appName, RSocketServiceProperties rsocketServiceProperties) {
            this.appName = appName;
            this.rsocketServiceProperties = rsocketServiceProperties;
        }

        public Builder binderCustomizers(List<RSocketBinderCustomizer> binderCustomizers) {
            this.binderCustomizers = binderCustomizers;
            return this;
        }

        public Builder requesterSupportBuilderCustomizers(List<RSocketRequesterSupportCustomizer> requesterSupportCustomizers) {
            this.requesterSupportCustomizers = requesterSupportCustomizers;
            return this;
        }

        public Builder healthCheck(HealthCheck customHealthCheck) {
            this.customHealthCheck = customHealthCheck;
            return this;
        }

        public Builder tracer(Tracer tracer) {
            this.tracer = tracer;
            return this;
        }

        public RSocketServiceRequester build() {
            return new RSocketServiceRequester(appName, rsocketServiceProperties, tracer, binderCustomizers, requesterSupportCustomizers, customHealthCheck);
        }

        public RSocketServiceRequester buildAndInit() {
            RSocketServiceRequester requester = build();
            requester.init();
            return requester;
        }
    }
}
