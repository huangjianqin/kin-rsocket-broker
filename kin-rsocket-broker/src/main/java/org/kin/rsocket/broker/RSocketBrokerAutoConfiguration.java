package org.kin.rsocket.broker;

import io.rsocket.RSocket;
import org.kin.rsocket.broker.cluster.DefaultBrokerManager;
import org.kin.rsocket.broker.cluster.RSocketBrokerManager;
import org.kin.rsocket.broker.config.ConfDiamond;
import org.kin.rsocket.broker.config.MemoryStorageConfDiamond;
import org.kin.rsocket.broker.controller.AppQueryController;
import org.kin.rsocket.broker.controller.ConfigController;
import org.kin.rsocket.broker.controller.ServiceQueryController;
import org.kin.rsocket.broker.event.*;
import org.kin.rsocket.broker.security.AuthenticationService;
import org.kin.rsocket.broker.security.JwtAuthenticationService;
import org.kin.rsocket.broker.services.BrokerDiscoveryService;
import org.kin.rsocket.broker.services.HealthService;
import org.kin.rsocket.core.*;
import org.kin.rsocket.core.discovery.DiscoveryService;
import org.kin.rsocket.core.event.CloudEventConsumer;
import org.kin.rsocket.core.event.CloudEventConsumers;
import org.kin.rsocket.core.event.CloudEventData;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import reactor.extra.processor.TopicProcessor;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.stream.Collectors;

/**
 * @author huangjianqin
 * @date 2021/2/15
 */
@Configuration
@EnableConfigurationProperties(RSocketBrokerProperties.class)
public class RSocketBrokerAutoConfiguration {
    @Autowired
    private RSocketBrokerProperties brokerConfig;

    /**
     * 接受cloud event的flux
     */
    @Bean
    public TopicProcessor<CloudEventData<?>> cloudEventProcessor() {
        return TopicProcessor.<CloudEventData<?>>builder().name("cloud-events-processor").build();
    }

    /**
     * 接受tips的flux
     */
    @Bean
    public TopicProcessor<String> notificationProcessor() {
        return TopicProcessor.<String>builder().name("notifications-processor").bufferSize(8).build();
    }

    //----------------------------------------------cloud event consumers----------------------------------------------

    /**
     * 管理所有{@link CloudEventConsumer}的实例
     */
    @Bean
    public CloudEventConsumers cloudEventConsumers(@Autowired TopicProcessor<CloudEventData<?>> cloudEventProcessor,
                                                   ObjectProvider<CloudEventConsumer> consumers) {
        CloudEventConsumers cloudEventConsumers = new CloudEventConsumers(cloudEventProcessor);
        cloudEventConsumers.addConsumers(consumers.stream().collect(Collectors.toList()));
        return cloudEventConsumers;
    }

    @Bean
    public AppStatusEventConsumer appStatusEventConsumer() {
        return new AppStatusEventConsumer();
    }

    @Bean
    public PortsUpdateEventConsumer portsUpdateEventConsumer() {
        return new PortsUpdateEventConsumer();
    }

    @Bean
    public ServicesExposedEventConsumer servicesExposedEventConsumer() {
        return new ServicesExposedEventConsumer();
    }

    @Bean
    public ServicesHiddenEventConsumer servicesHiddenEventConsumer() {
        return new ServicesHiddenEventConsumer();
    }

    @Bean
    public UpstreamClusterChangedEventConsumer upstreamClusterChangedEventConsumer() {
        return new UpstreamClusterChangedEventConsumer();
    }
    //----------------------------------------------

    @Bean
    public RSocketFilterChain rsocketFilterChain(ObjectProvider<AbstractRSocketFilter> filters) {
        return new RSocketFilterChain(filters.orderedStream().collect(Collectors.toList()));
    }

    @Bean
    public ServiceMeshInspector serviceMeshInspector() {
        return new ServiceMeshInspector(brokerConfig.isAuthRequired());
    }

    /**
     * 默认服务注册中心
     */
    @Bean
    @ConditionalOnMissingBean(ReactiveServiceRegistry.class)
    public ReactiveServiceRegistry serviceRegistry() {
        return new DefaultServiceRegistry();
    }

    /**
     * {@link RSocketService}注解processor
     */
    @Bean
    public RSocketServiceAnnoProcessor rsocketServiceAnnoProcessor(@Autowired ReactiveServiceRegistry serviceRegistry) {
        return new RSocketServiceAnnoProcessor(serviceRegistry);
    }

    @Bean
    public ServiceRouteTable serviceRouteTable() {
        return new ServiceRouteTable();
    }

    @Bean
    public ServiceRouter serviceRouter(@Autowired ReactiveServiceRegistry serviceRegistry,
                                       @Autowired RSocketFilterChain rsocketFilterChain,
                                       @Autowired ServiceRouteTable serviceRouteTable,
                                       @Autowired @Qualifier("cloudEventProcessor") TopicProcessor<CloudEventData<?>> eventProcessor,
                                       @Autowired @Qualifier("notificationProcessor") TopicProcessor<String> notificationProcessor,
                                       @Autowired AuthenticationService authenticationService,
                                       @Autowired RSocketBrokerManager rSocketBrokerManager,
                                       @Autowired ServiceMeshInspector serviceMeshInspector,
                                       @Autowired RSocketBrokerProperties properties,
                                       @Autowired @Qualifier("upstreamBrokerCluster") RSocket upstreamBrokerCluster) {
        return new ServiceRouter(serviceRegistry, rsocketFilterChain, serviceRouteTable,
                eventProcessor, notificationProcessor, authenticationService, rSocketBrokerManager, serviceMeshInspector,
                properties.isAuthRequired(), upstreamBrokerCluster);
    }

    //----------------------------------------------broker binder相关----------------------------------------------
    @Bean(initMethod = "start", destroyMethod = "close")
    public RSocketBinder rsocketListener(ObjectProvider<RSocketBinderBuilderCustomizer> customizers) {
        RSocketBinder.Builder builder = RSocketBinder.builder();
        customizers.orderedStream().forEach((customizer) -> customizer.customize(builder));
        return builder.build();
    }

    @Bean
    @Order(100)
    public RSocketBinderBuilderCustomizer defaultRSocketListenerCustomizer(@Autowired ServiceRouter serviceRouter) {
        return builder -> {
            builder.acceptor(serviceRouter.acceptor());
            builder.listen("tcp", brokerConfig.getPort());
        };
    }

    @Bean
    @Order(101)
    @ConditionalOnProperty(name = "rsocket.broker.ssl.key-store")
    public RSocketBinderBuilderCustomizer rsocketListenerSSLCustomizer(@Autowired ResourceLoader resourceLoader) {
        return builder -> {
            RSocketBrokerProperties.RSocketSSL rsocketSSL = brokerConfig.getSsl();
            if (rsocketSSL != null && rsocketSSL.isEnabled() && rsocketSSL.getKeyStore() != null) {
                try {
                    KeyStore store = KeyStore.getInstance("PKCS12");
                    store.load(resourceLoader.getResource(rsocketSSL.getKeyStore()).getInputStream(), rsocketSSL.getKeyStorePassword().toCharArray());
                    String alias = store.aliases().nextElement();
                    Certificate certificate = store.getCertificate(alias);
                    KeyStore.Entry entry = store.getEntry(alias, new KeyStore.PasswordProtection(rsocketSSL.getKeyStorePassword().toCharArray()));
                    PrivateKey privateKey = ((KeyStore.PrivateKeyEntry) entry).getPrivateKey();
                    builder.sslContext(certificate, privateKey);
                    builder.listen("tcps", brokerConfig.getPort());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
    //----------------------------------------------

    @Bean
    @ConditionalOnMissingBean(RSocketBrokerManager.class)
    public RSocketBrokerManager rsocketBrokerManager() {
        return new DefaultBrokerManager();
    }

    @Bean
    @ConditionalOnProperty(name = "rsocket.broker.upstream-brokers")
    public SubBrokerRequester subBrokerRequester(@Autowired Environment env,
                                                 @Autowired ServiceRouteTable serviceRouteTable,
                                                 @Autowired ServiceRouter serviceRouter,
                                                 @Autowired RSocketFilterChain filterChain,
                                                 @Autowired @Qualifier("cloudEventProcessor") TopicProcessor<CloudEventData<?>> eventProcessor) {
        return new SubBrokerRequester(brokerConfig, env, serviceRouteTable, serviceRouter, filterChain, eventProcessor);
    }

    @Bean
    @ConditionalOnProperty(name = "rsocket.broker.upstream-brokers")
    public RSocket upstreamBrokerCluster(@Autowired SubBrokerRequester subBrokerRequester) {
        return UpstreamCluster.brokerUpstreamCluster(subBrokerRequester, brokerConfig.getUpstreamBrokers());
    }

    //----------------------------------------------services

    /**
     * 默认基于内存的配置中心实现
     */
    @Bean
    @ConditionalOnMissingBean
    public ConfDiamond configurationService() {
        return new MemoryStorageConfDiamond();
    }

    @Bean
    public DiscoveryService discoveryService() {
        return new BrokerDiscoveryService();
    }

    @Bean
    public AuthenticationService authenticationService() throws Exception {
        return new JwtAuthenticationService();
    }

    @Bean
    public HealthService healthService(@Autowired ServiceRouteTable serviceRouteTable) {
        return new HealthService(serviceRouteTable);
    }
    //----------------------------------------------

    //----------------------------------------------controller----------------------------------------------
    @Bean
    public ConfigController configController() {
        return new ConfigController();
    }

    @Bean
    public AppQueryController appQueryController() {
        return new AppQueryController();
    }

    @Bean
    public ServiceQueryController serviceQueryController() {
        return new ServiceQueryController();
    }
    //----------------------------------------------
}
