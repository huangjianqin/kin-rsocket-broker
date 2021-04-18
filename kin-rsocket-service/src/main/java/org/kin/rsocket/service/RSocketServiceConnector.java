package org.kin.rsocket.service;

import org.kin.rsocket.core.*;
import org.kin.rsocket.core.domain.AppStatus;
import org.kin.rsocket.core.event.*;
import org.kin.rsocket.core.health.HealthCheck;
import org.kin.rsocket.service.event.UpstreamClusterChangedEventConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * all in one
 *
 * @author huangjianqin
 * @date 2021/3/28
 */
public final class RSocketServiceConnector implements UpstreamClusterManager {
    private static final Logger log = LoggerFactory.getLogger(RSocketServiceConnector.class);
    /** app name */
    private final String appName;
    /** 配置 */
    private final RSocketServiceProperties config;
    /** upstream cluster manager */
    private UpstreamClusterManagerImpl upstreamClusterManager;
    /** requester连接配置 */
    private RequesterSupport requesterSupport;
    /** rsocket binder */
    private RSocketBinder binder;

    public RSocketServiceConnector(String appName,
                                   RSocketServiceProperties config) {
        this.appName = appName;
        this.config = config;

        //create requester support
        requesterSupport = new RequesterSupportImpl(config, appName);

        //init upstream manager
        upstreamClusterManager = new UpstreamClusterManagerImpl(requesterSupport);
        add(config);
    }

    /**
     * 初始化, 并创建connection
     */
    public void connect() {
        connect(Collections.emptyList(), Collections.emptyList());
    }

    /**
     * 初始化, 并创建connection
     */
    public void connect(List<RSocketBinderBuilderCustomizer> binderBuilderCustomizers,
                        List<RequesterSupportBuilderCustomizer> requesterSupportBuilderCustomizers) {
        connect(binderBuilderCustomizers, requesterSupportBuilderCustomizers, null);
    }

    /**
     * 初始化, 并创建connection
     */
    public void connect(List<RSocketBinderBuilderCustomizer> binderBuilderCustomizers,
                        List<RequesterSupportBuilderCustomizer> requesterSupportBuilderCustomizers,
                        HealthCheck customHealthCheck) {
        //bind
        if (config.getPort() > 0) {
            RSocketBinder.Builder binderBuilder = RSocketBinder.builder();
            binderBuilder.acceptor((setupPayload, requester) -> Mono.just(new Responder(requester, setupPayload)));
            binderBuilder.listen(config.getSchema(), config.getPort());
            binderBuilderCustomizers.forEach((customizer) -> customizer.customize(binderBuilder));
            binder = binderBuilder.build();
            binder.start();
        }

        //custom requester support
        requesterSupportBuilderCustomizers.forEach((customizer) -> customizer.customize((RequesterSupportImpl) requesterSupport));

        CloudEventConsumers.INSTANCE.addConsumer(new UpstreamClusterChangedEventConsumer(upstreamClusterManager));

        //register health check
        if (Objects.isNull(customHealthCheck)) {
            customHealthCheck = new HealthCheckImpl();
        }
        registerService(HealthCheck.class, customHealthCheck);
    }

    /**
     * 初始化, 然后创建connection, 并向broker暴露服务
     */
    public void connectAndPub() {
        connectAndPub(Collections.emptyList(), Collections.emptyList());
    }

    /**
     * 初始化, 然后创建connection, 并向broker暴露服务
     */
    public void connectAndPub(List<RSocketBinderBuilderCustomizer> binderBuilderCustomizers,
                              List<RequesterSupportBuilderCustomizer> requesterSupportBuilderCustomizers) {
        connectAndPub(binderBuilderCustomizers, requesterSupportBuilderCustomizers, null);
    }

    /**
     * 初始化, 然后创建connection, 并向broker暴露服务
     */
    public void connectAndPub(List<RSocketBinderBuilderCustomizer> binderBuilderCustomizers,
                              List<RequesterSupportBuilderCustomizer> requesterSupportBuilderCustomizers,
                              HealthCheck customHealthCheck) {
        connect(binderBuilderCustomizers, requesterSupportBuilderCustomizers, customHealthCheck);
        publishServices();
    }

    /**
     * 注册service
     */
    public RSocketServiceConnector registerService(Class<?> serviceInterface, Object provider) {
        return registerService("", "", serviceInterface, provider);
    }

    /**
     * 注册service
     */
    public RSocketServiceConnector registerService(String group, String version, Class<?> serviceInterface, Object provider) {
        ReactiveServiceRegistry.INSTANCE.addProvider(group, version, serviceInterface, provider);
        return this;
    }

    /**
     * 注册service
     */
    public RSocketServiceConnector registerService(String serviceName, Class<?> serviceInterface, Object provider) {
        return registerService("", serviceName, "", serviceInterface, provider);
    }

    /**
     * 注册service
     */
    public RSocketServiceConnector registerService(String group, String serviceName, String version, Class<?> serviceInterface, Object provider) {
        ReactiveServiceRegistry.INSTANCE.addProvider(group, serviceName, version, serviceInterface, provider);
        return this;
    }

    /**
     * 注册并发布service
     */
    public RSocketServiceConnector registerAndPubService(Class<?> serviceInterface, Object provider) {
        return registerAndPubService("", "", serviceInterface, provider);
    }

    /**
     * 注册并发布service
     */
    public RSocketServiceConnector registerAndPubService(String group, String version, Class<?> serviceInterface, Object provider) {
        registerService(group, version, serviceInterface, provider);
        publishService(group, serviceInterface.getCanonicalName(), version);
        return this;
    }

    /**
     * 注册并发布service
     */
    public RSocketServiceConnector registerAndPubService(String serviceName, Class<?> serviceInterface, Object provider) {
        return registerAndPubService("", serviceName, "", serviceInterface, provider);
    }

    /**
     * 注册并发布service
     */
    public RSocketServiceConnector registerAndPubService(String group, String serviceName, String version, Class<?> serviceInterface, Object provider) {
        registerService(group, serviceName, version, serviceInterface, provider);
        publishService(group, serviceName, version);
        return this;
    }

    /**
     * 发布(暴露)服务
     */
    public void publishServices() {
        //broker uris
        String brokerUris = String.join(",", config.getBrokers());

        CloudEventData<AppStatusEvent> appStatusEventCloudEvent = CloudEventBuilder
                .builder(AppStatusEvent.serving(RSocketAppContext.ID))
                .build();

        // app status
        getBroker().broadcastCloudEvent(appStatusEventCloudEvent)
                .doOnSuccess(aVoid -> log.info(String.format("Application connected with RSocket Brokers(%s) successfully", brokerUris)))
                .subscribe();

        // service exposed
        CloudEventData<ServicesExposedEvent> servicesExposedEventCloudEvent = ReactiveServiceRegistry.servicesExposedEvent();
        if (servicesExposedEventCloudEvent != null) {
            publishServices(servicesExposedEventCloudEvent);
        }
    }

    /**
     * 通知broker暴露新服务
     */
    private void publishServices(CloudEventData<ServicesExposedEvent> servicesExposedEventCloudEvent) {
        getBroker().broadcastCloudEvent(servicesExposedEventCloudEvent)
                .doOnSuccess(aVoid -> {
                    //broker uris
                    String brokerUris = String.join(",", config.getBrokers());
                    String exposedServiceGsvs = ReactiveServiceRegistry.exposedServices().stream().map(ServiceLocator::getGsv).collect(Collectors.joining(","));
                    log.info(String.format("Services(%s) published on Brokers(%s)!.", exposedServiceGsvs, brokerUris));
                }).subscribe();
    }

    /**
     * 通知broker暴露新服务
     */
    private void publishService(String group, String serviceName, String version) {
        //publish
        CloudEventData<ServicesExposedEvent> cloudEvent = ServicesExposedEvent.of(ServiceLocator.of(group, serviceName, version));
        publishServices(cloudEvent);
    }

    /**
     * 移除服务
     */
    public void removeService(String serviceName, Class<?> serviceInterface) {
        removeService("", serviceName, "", serviceInterface);
    }

    /**
     * 移除服务
     */
    public void removeService(String group, String serviceName, String version, Class<?> serviceInterface) {
        ServiceLocator targetServiceLocator = ServiceLocator.of(group, serviceName, version);
        CloudEventData<ServicesHiddenEvent> cloudEvent = ServicesHiddenEvent.of(Collections.singletonList(targetServiceLocator));
        getBroker().broadcastCloudEvent(cloudEvent)
                .doOnSuccess(unused -> {
                    //broker uris
                    String brokerUris = String.join(",", config.getBrokers());

                    ReactiveServiceRegistry.INSTANCE.removeProvider(group, serviceName, version, serviceInterface);
                    log.info(String.format("Services(%s) hide on Brokers(%s)!.", serviceName, brokerUris));
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

    @Override
    public void close() {
        upstreamClusterManager.close();
        if (Objects.nonNull(binder)) {
            binder.close();
        }
    }

    //------------------------------------------------------------------------------------------------------------------------

    /**
     * 内置health check, 只要broker正常, 本application就可以对外提供服务
     */
    private class HealthCheckImpl implements HealthCheck {
        /** broker health check */
        private final HealthCheck brokerHealthCheck;

        public HealthCheckImpl() {
            brokerHealthCheck = ServiceReferenceBuilder
                    .requester(HealthCheck.class)
                    //todo 看看编码方式是否需要修改
                    .nativeImage()
                    .upstreamClusterManager(upstreamClusterManager)
                    .build();
        }

        @Override
        public Mono<Integer> check(String serviceName) {
            return Mono.just(AppStatus.SERVING.equals(brokerHealthCheck.check(null)) ? 1 : 0);
        }
    }

    //--------------------------------------------------overwrite UpstreamClusterManager----------------------------------------------------------------------
    @Override
    public void add(String group, String serviceName, String version, List<String> uris) {
        upstreamClusterManager.add(group, serviceName, version, uris);
    }

    @Override
    public void add(RSocketServiceProperties config) {
        upstreamClusterManager.add(config);
    }

    @Override
    public Collection<UpstreamCluster> getAll() {
        return upstreamClusterManager.getAll();
    }

    @Override
    public UpstreamCluster get(String serviceId) {
        return upstreamClusterManager.get(serviceId);
    }

    @Override
    public UpstreamCluster getBroker() {
        return upstreamClusterManager.getBroker();
    }

    @Override
    public void refresh(String serviceId, List<String> uris) {
        upstreamClusterManager.refresh(serviceId, uris);
    }

    @Override
    public RequesterSupport getRequesterSupport() {
        return upstreamClusterManager.getRequesterSupport();
    }
    //--------------------------------------------------overwrite UpstreamClusterManager----------------------------------------------------------------------
}
