package org.kin.rsocket.service;

import brave.Tracer;
import io.cloudevents.CloudEvent;
import org.kin.rsocket.core.*;
import org.kin.rsocket.core.domain.RSocketServiceInfo;
import org.kin.rsocket.core.event.*;
import org.kin.rsocket.core.health.HealthCheck;
import org.kin.rsocket.service.event.ServiceInstanceChangedEventConsumer;
import org.kin.rsocket.service.event.UpstreamClusterChangedEventConsumer;
import org.kin.rsocket.service.health.BrokerHealthCheckService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * all in one
 * broker(upstream service) client, 包含绑定rsocket server, 连接broker, 注册服务, 注销服务, 添加cloud event consumer, broker连接管理, app应用状态管理和p2p服务请求等功能
 *
 * @author huangjianqin
 * @date 2021/3/28
 */
public final class RSocketBrokerClient implements UpstreamClusterManager {
    private static final Logger log = LoggerFactory.getLogger(RSocketBrokerClient.class);

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
    /** upstream cluster manager */
    private final UpstreamClusterManagerImpl upstreamClusterManager;
    /** requester连接配置 */
    private final RSocketRequesterSupportImpl requesterSupport;
    /** rsocket server */
    private final RSocketServer rsocketServer;
    /** 状态 */
    private final AtomicInteger state = new AtomicInteger(STATE_INIT);

    private RSocketBrokerClient(String appName,
                                RSocketServiceProperties rsocketServiceProperties,
                                Tracer tracer,
                                List<RSocketBinderCustomizer> binderCustomizers,
                                List<RSocketRequesterSupportCustomizer> requesterSupportCustomizers,
                                HealthCheck customHealthCheck) {
        this.appName = appName;
        this.rsocketServiceProperties = rsocketServiceProperties;

        //1. create server
        int rsocketBindPort = rsocketServiceProperties.getPort();
        if (rsocketBindPort > 0) {
            RSocketServer.Builder builder = RSocketServer.builder();
            builder.acceptor((setupPayload, requester) -> Mono.just(new RSocketBrokerOrServiceRequestHandler(requester, setupPayload, tracer)));
            builder.listen(rsocketServiceProperties.getSchema(), rsocketBindPort);
            binderCustomizers.forEach((customizer) -> customizer.customize(builder));
            rsocketServer = builder.build();
        } else {
            rsocketServer = null;
        }

        //2.1 create requester support
        requesterSupport = new RSocketRequesterSupportImpl(rsocketServiceProperties, appName, tracer);
        //2.2 custom requester support
        requesterSupportCustomizers.forEach((customizer) -> customizer.customize(requesterSupport));
        //2.3 init upstream manager
        upstreamClusterManager = new UpstreamClusterManagerImpl(requesterSupport, rsocketServiceProperties.getLoadBalance());

        //3. register health check
        if (Objects.isNull(customHealthCheck)) {
            customHealthCheck = new BrokerHealthCheckService(upstreamClusterManager);
        }
        registerService(HealthCheck.class, customHealthCheck);

        //4. add internal cloud event consumer
        CloudEventBus.INSTANCE.addConsumer(new UpstreamClusterChangedEventConsumer(upstreamClusterManager));
        CloudEventBus.INSTANCE.addConsumer(new ServiceInstanceChangedEventConsumer(upstreamClusterManager));
    }

    /**
     * 初始化requester
     */
    public void create() {
        if (!state.compareAndSet(STATE_INIT, STATE_START)) {
            return;
        }

        //1. bind
        if (Objects.nonNull(rsocketServer)) {
            rsocketServer.bind();
        }

        //2. connect
        add(rsocketServiceProperties);
    }

    /**
     * 某些接口调用需要检查是否已启动
     */
    private void checkStart() {
        if (state.get() != STATE_START) {
            throw new IllegalStateException("RSocketBrokerClient doesn't init");
        }
    }

    /**
     * 注册service
     */
    public RSocketBrokerClient registerService(Class<?> serviceInterface, Object provider) {
        return registerService(rsocketServiceProperties.getGroup(), rsocketServiceProperties.getVersion(), serviceInterface, provider);
    }

    /**
     * 注册service
     */
    public RSocketBrokerClient registerService(String group, String version, Class<?> serviceInterface, Object provider, String... tags) {
        LocalRSocketServiceRegistry.INSTANCE.addProvider(group, version, serviceInterface, provider, tags);
        return this;
    }

    /**
     * 注册service
     */
    public RSocketBrokerClient registerService(String service, Class<?> serviceInterface, Object provider, String... tags) {
        return registerService(rsocketServiceProperties.getGroup(), service, rsocketServiceProperties.version, serviceInterface, provider, tags);
    }

    /**
     * 注册service
     */
    public RSocketBrokerClient registerService(String group, String service, String version, Class<?> serviceInterface, Object provider, String... tags) {
        LocalRSocketServiceRegistry.INSTANCE.addProvider(group, service, version, serviceInterface, provider, tags);
        return this;
    }

    /**
     * 注册service
     * 供cloud function使用, 因为其无法获取真实的service信息, 所以, 只能在外部从spring function registry中尽量提取service信息, 然后进行注册
     */
    public RSocketBrokerClient registerService(String handler, Object provider, ReactiveMethodInvoker invoker, RSocketServiceInfo serviceInfo) {
        LocalRSocketServiceRegistry.INSTANCE.addProvider(handler, provider, invoker, serviceInfo);
        return this;
    }

    /**
     * 注册并发布service
     */
    public RSocketBrokerClient registerAndPubService(Class<?> serviceInterface, Object provider) {
        return registerAndPubService(rsocketServiceProperties.getGroup(), rsocketServiceProperties.getVersion(), serviceInterface, provider);
    }

    /**
     * 注册并发布service
     */
    public RSocketBrokerClient registerAndPubService(String group, String version, Class<?> serviceInterface, Object provider) {
        registerService(group, version, serviceInterface, provider);
        publishService(group, serviceInterface.getName(), version);
        return this;
    }

    /**
     * 注册并发布service
     */
    public RSocketBrokerClient registerAndPubService(String service, Class<?> serviceInterface, Object provider) {
        return registerAndPubService(rsocketServiceProperties.getGroup(), service, rsocketServiceProperties.getVersion(), serviceInterface, provider);
    }

    /**
     * 注册并发布service
     */
    public RSocketBrokerClient registerAndPubService(String group, String service, String version, Class<?> serviceInterface, Object provider) {
        registerService(group, service, version, serviceInterface, provider);
        publishService(group, service, version);
        return this;
    }

    /**
     * 注册并发布service
     * 供cloud function使用, 因为其无法获取真实的service信息, 所以, 只能在外部从spring function registry中尽量提取service信息, 然后进行注册
     */
    public RSocketBrokerClient registerAndPubService(String handler, Object provider, ReactiveMethodInvoker invoker, RSocketServiceInfo serviceInfo) {
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
        CloudEvent cloudEvent = LocalRSocketServiceRegistry.servicesExposedCloudEvent();
        if (cloudEvent != null) {
            publishServices(cloudEvent);
        }
    }

    /**
     * 通知broker暴露新服务
     */
    private void publishServices(CloudEvent cloudEvent) {
        UpstreamCluster broker = getBroker();
        if (Objects.isNull(broker)) {
            return;
        }

        broker.broadcastCloudEvent(cloudEvent)
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
        //publish services exposed event
        publishServices(RSocketServicesExposedEvent.of(ServiceLocator.of(group, service, version)).toCloudEvent());
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
        CloudEvent cloudEvent = RSocketServicesHiddenEvent.of(Collections.singletonList(targetServiceLocator)).toCloudEvent();
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
        CloudEventBus.INSTANCE.addConsumers(consumer);
    }

    /**
     * 批量添加{@link CloudEventConsumer}
     */
    public void addConsumers(CloudEventConsumer... consumers) {
        CloudEventBus.INSTANCE.addConsumers(Arrays.asList(consumers));
    }

    /**
     * 批量添加{@link CloudEventConsumer}
     */
    public void addConsumers(Collection<CloudEventConsumer> consumers) {
        CloudEventBus.INSTANCE.addConsumers(consumers);
    }

    /**
     * 设置app状态为提供服务中, 并暴露所有已注册的服务
     */
    public void serving() {
        UpstreamCluster broker = getBroker();
        if (Objects.nonNull(broker)) {
            //notify broker app status update
            broker.broadcastCloudEvent(AppStatusEvent.serving(RSocketAppContext.ID).toCloudEvent())
                    .doOnSuccess(aVoid -> log.info(String.format("application service serving on RSocket Brokers(%s) successfully", String.join(",", rsocketServiceProperties.getBrokers()))))
                    .subscribe();
            publishServices();
        }
    }

    /**
     * 设置app状态为服务下线, 即broker不会路由到这些服务
     */
    public void down() {
        UpstreamCluster broker = getBroker();
        if (Objects.nonNull(broker)) {
            //notify broker app status update
            broker.broadcastCloudEvent(AppStatusEvent.down(RSocketAppContext.ID).toCloudEvent())
                    .doOnSuccess(aVoid -> log.info(String.format("application service down on RSocket Brokers(%s) successfully", String.join(",", rsocketServiceProperties.getBrokers()))))
                    .subscribe();
        }
    }

    @Override
    public void dispose() {
        if (!state.compareAndSet(STATE_START, STATE_TERMINATED)) {
            return;
        }

        upstreamClusterManager.dispose();
        if (Objects.nonNull(rsocketServer)) {
            rsocketServer.dispose();
        }
    }

    @Override
    public boolean isDisposed() {
        return state.get() == STATE_TERMINATED;
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

    //--------------------------------------------------getter----------------------------------------------------------------------
    public String getAppName() {
        return appName;
    }

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

        public RSocketBrokerClient build() {
            return new RSocketBrokerClient(appName, rsocketServiceProperties, tracer, binderCustomizers, requesterSupportCustomizers, customHealthCheck);
        }

        public RSocketBrokerClient buildAndInit() {
            RSocketBrokerClient brokerClient = build();
            brokerClient.create();
            return brokerClient;
        }
    }
}
