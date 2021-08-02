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
 * broker requester, 包含绑定rsocket server, 连接broker, 注册服务, 注销服务, 添加cloud event consumer和broker连接管理等功能
 *
 * @author huangjianqin
 * @date 2021/3/28
 */
public final class RSocketServiceRequester implements UpstreamClusterManager {
    private static final Logger log = LoggerFactory.getLogger(RSocketServiceRequester.class);
    /** app name */
    private final String appName;
    /** 配置 */
    private final RSocketServiceProperties config;
    /** upstream cluster manager */
    private UpstreamClusterManagerImpl upstreamClusterManager;
    /** requester连接配置 */
    private RSocketRequesterSupportImpl requesterSupport;
    /** rsocket binder */
    private RSocketBinder binder;

    private RSocketServiceRequester(String appName,
                                    RSocketServiceProperties config) {
        this.appName = appName;
        this.config = config;
    }

    /**
     * 注册service
     */
    public RSocketServiceRequester registerService(Class<?> serviceInterface, Object provider) {
        return registerService("", "", serviceInterface, provider);
    }

    /**
     * 注册service
     */
    public RSocketServiceRequester registerService(String group, String version, Class<?> serviceInterface, Object provider, String... tags) {
        RSocketServiceRegistry.INSTANCE.addProvider(group, version, serviceInterface, provider, tags);
        return this;
    }

    /**
     * 注册service
     */
    public RSocketServiceRequester registerService(String serviceName, Class<?> serviceInterface, Object provider, String... tags) {
        return registerService("", serviceName, "", serviceInterface, provider, tags);
    }

    /**
     * 注册service
     */
    public RSocketServiceRequester registerService(String group, String serviceName, String version, Class<?> serviceInterface, Object provider, String... tags) {
        RSocketServiceRegistry.INSTANCE.addProvider(group, serviceName, version, serviceInterface, provider, tags);
        return this;
    }

    /**
     * 注册并发布service
     */
    public RSocketServiceRequester registerAndPubService(Class<?> serviceInterface, Object provider) {
        return registerAndPubService("", "", serviceInterface, provider);
    }

    /**
     * 注册并发布service
     */
    public RSocketServiceRequester registerAndPubService(String group, String version, Class<?> serviceInterface, Object provider) {
        registerService(group, version, serviceInterface, provider);
        publishService(group, serviceInterface.getCanonicalName(), version);
        return this;
    }

    /**
     * 注册并发布service
     */
    public RSocketServiceRequester registerAndPubService(String serviceName, Class<?> serviceInterface, Object provider) {
        return registerAndPubService("", serviceName, "", serviceInterface, provider);
    }

    /**
     * 注册并发布service
     */
    public RSocketServiceRequester registerAndPubService(String group, String serviceName, String version, Class<?> serviceInterface, Object provider) {
        registerService(group, serviceName, version, serviceInterface, provider);
        publishService(group, serviceName, version);
        return this;
    }

    /**
     * 获取broker urls字符串, 以,分割
     */
    private String getBrokerUris() {
        return String.join(",", config.getBrokers());
    }

    /**
     * 发布(暴露)服务
     */
    public void publishServices() {
        // service exposed
        CloudEventData<RSocketServicesExposedEvent> servicesExposedEventCloudEvent = RSocketServiceRegistry.servicesExposedEvent();
        if (servicesExposedEventCloudEvent != null) {
            publishServices(servicesExposedEventCloudEvent);
        }
    }

    /**
     * 通知broker暴露新服务
     */
    private void publishServices(CloudEventData<RSocketServicesExposedEvent> servicesExposedEventCloudEvent) {
        getBroker().broadcastCloudEvent(servicesExposedEventCloudEvent)
                .doOnSuccess(aVoid -> {
                    //broker uris
                    String brokerUris = String.join(",", config.getBrokers());
                    String exposedServiceGsvs = RSocketServiceRegistry.exposedServices().stream().map(ServiceLocator::getGsv).collect(Collectors.joining(", "));
                    log.info(String.format("services(%s) published on Brokers(%s)!", exposedServiceGsvs, brokerUris));
                }).subscribe();
    }

    /**
     * 通知broker暴露新服务
     */
    private void publishService(String group, String serviceName, String version) {
        //publish
        CloudEventData<RSocketServicesExposedEvent> cloudEvent = RSocketServicesExposedEvent.of(ServiceLocator.of(group, serviceName, version));
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
        CloudEventData<RSocketServicesHiddenEvent> cloudEvent = RSocketServicesHiddenEvent.of(Collections.singletonList(targetServiceLocator));
        getBroker().broadcastCloudEvent(cloudEvent)
                .doOnSuccess(unused -> {
                    //broker uris
                    String brokerUris = String.join(",", config.getBrokers());

                    RSocketServiceRegistry.INSTANCE.removeProvider(group, serviceName, version, serviceInterface);
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
    public RSocketRequesterSupport getRequesterSupport() {
        return upstreamClusterManager.getRequesterSupport();
    }
    //--------------------------------------------------overwrite UpstreamClusterManager----------------------------------------------------------------------

    //--------------------------------------------------内部类----------------------------------------------------------------------
    public static Builder builder(String appName, RSocketServiceProperties config) {
        return new Builder(appName, config);
    }

    /** builder **/
    public static class Builder {
        private final RSocketServiceRequester requester;
        private List<RSocketBinderCustomizer> binderCustomizers = Collections.emptyList();
        private List<RSocketRequesterSupportCustomizer> requesterSupportCustomizers = Collections.emptyList();
        private HealthCheck customHealthCheck;

        public Builder(String appName, RSocketServiceProperties config) {
            requester = new RSocketServiceRequester(appName, config);
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

        public RSocketServiceRequester build() {
            RSocketServiceProperties config = requester.config;
            //1. bind
            if (config.getPort() > 0) {
                RSocketBinder.Builder binderBuilder = RSocketBinder.builder();
                binderBuilder.acceptor((setupPayload, requester) -> Mono.just(new BrokerOrServiceRequestHandler(requester, setupPayload)));
                binderBuilder.listen(config.getSchema(), config.getPort());
                binderCustomizers.forEach((customizer) -> customizer.customize(binderBuilder));
                requester.binder = binderBuilder.build();
                requester.binder.bind();
            }

            //2. connect
            //2.1 create requester support
            requester.requesterSupport = new RSocketRequesterSupportImpl(config, requester.appName);

            //2.2 custom requester support
            requesterSupportCustomizers.forEach((customizer) -> customizer.customize(requester.requesterSupport));

            //2.3 init upstream manager
            requester.upstreamClusterManager = new UpstreamClusterManagerImpl(requester.requesterSupport);
            requester.add(config);

            //2.4 add internal consumer
            CloudEventConsumers.INSTANCE.addConsumer(new UpstreamClusterChangedEventConsumer(requester.upstreamClusterManager));

            //3. register health check
            if (Objects.isNull(customHealthCheck)) {
                customHealthCheck = new HealthCheckImpl(requester.upstreamClusterManager);
            }
            requester.registerService(HealthCheck.class, customHealthCheck);

            //4. notify broker app status update
            CloudEventData<AppStatusEvent> appStatusEventCloudEvent = CloudEventBuilder
                    .builder(AppStatusEvent.serving(RSocketAppContext.ID))
                    .build();

            requester.getBroker().broadcastCloudEvent(appStatusEventCloudEvent)
                    .doOnSuccess(aVoid -> log.info(String.format("application connected with RSocket Brokers(%s) successfully", requester.getBrokerUris())))
                    .subscribe();

            return requester;
        }
    }

    /**
     * 内置health check, 只要broker正常, 本application就可以对外提供服务
     */
    private static class HealthCheckImpl implements HealthCheck {
        /** broker health check */
        private final HealthCheck brokerHealthCheck;

        public HealthCheckImpl(UpstreamClusterManagerImpl upstreamClusterManager) {
            brokerHealthCheck = RSocketServiceReferenceBuilder
                    .requester(HealthCheck.class)
                    .nativeImage()
                    .upstreamClusterManager(upstreamClusterManager)
                    .build();
        }

        @Override
        public Mono<Integer> check(String serviceName) {
            return brokerHealthCheck.check(null).map(r -> AppStatus.SERVING.getId() == r ? 1 : 0);
        }
    }
}
