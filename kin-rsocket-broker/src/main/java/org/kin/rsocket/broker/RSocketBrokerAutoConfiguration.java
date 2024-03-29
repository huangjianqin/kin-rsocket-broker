package org.kin.rsocket.broker;

import io.micrometer.core.instrument.MeterRegistry;
import io.rsocket.loadbalance.WeightedStatsRequestInterceptor;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.auth.AuthenticationService;
import org.kin.rsocket.broker.cluster.RSocketBrokerManager;
import org.kin.rsocket.broker.controller.*;
import org.kin.rsocket.broker.event.*;
import org.kin.rsocket.broker.services.CloudEventNotifyServiceImpl;
import org.kin.rsocket.broker.services.DiscoveryServiceImpl;
import org.kin.rsocket.broker.services.HealthService;
import org.kin.rsocket.core.RSocketBinderCustomizer;
import org.kin.rsocket.core.RSocketServer;
import org.kin.rsocket.core.RSocketServiceBeanPostProcessor;
import org.kin.rsocket.core.UpstreamCluster;
import org.kin.rsocket.core.discovery.DiscoveryService;
import org.kin.rsocket.core.event.CloudEventBus;
import org.kin.rsocket.core.event.CloudEventConsumer;
import org.kin.rsocket.core.event.CloudEventNotifyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import reactor.core.publisher.Sinks;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.List;

/**
 * 设计成auto configuration, 是为了参与auto configuration排序, 进而让配置中心的auto configuration排在前面, 优先加载
 *
 * @author huangjianqin
 * @date 2021/2/15
 */
@Configuration
@ConditionalOnBean(RSocketBrokerMarkerConfiguration.Marker.class)
@EnableConfigurationProperties(RSocketBrokerProperties.class)
public class RSocketBrokerAutoConfiguration {
    /**
     * 接受tips的flux
     */
    @Bean
    public Sinks.Many<String> notificationSink() {
        return Sinks.many().multicast().onBackpressureBuffer(8);
    }

    @Bean
    public Sinks.Many<String> p2pServiceNotificationSink() {
        return Sinks.many().multicast().onBackpressureBuffer(10000);
    }
    //----------------------------------------------cloud event consumers----------------------------------------------

    /**
     * 管理所有{@link CloudEventConsumer}的实例
     */
    @Bean(destroyMethod = "dispose")
    public CloudEventBus cloudEventBus(@Autowired List<CloudEventConsumer> consumers) {
        CloudEventBus.INSTANCE.addConsumers(consumers);
        return CloudEventBus.INSTANCE;
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
    public RSocketServicesExposedEventConsumer servicesExposedEventConsumer() {
        return new RSocketServicesExposedEventConsumer();
    }

    @Bean
    public RSocketServicesHiddenEventConsumer servicesHiddenEventConsumer() {
        return new RSocketServicesHiddenEventConsumer();
    }

    @Bean
    @ConditionalOnBean(name = "upstreamBrokerCluster", value = UpstreamCluster.class)
    public UpstreamClusterChangedEventConsumer upstreamClusterChangedEventConsumer() {
        return new UpstreamClusterChangedEventConsumer();
    }

    @Bean
    public FilterEnableEventConsumer filterEnableEventConsumer() {
        return new FilterEnableEventConsumer();
    }

    @Bean
    public P2pServiceChangedEventConsumer p2pServiceChangedEventConsumer() {
        return new P2pServiceChangedEventConsumer();
    }

    //----------------------------------------------

    @Bean
    public RSocketFilterChain rsocketFilterChain(@Autowired List<AbstractRSocketFilter> filters) {
        return new RSocketFilterChain(filters);
    }

    /**
     * {@link RSocketService}注解processor
     */
    @Bean
    public RSocketServiceBeanPostProcessor rsocketServiceBeanPostProcessor() {
        return new RSocketServiceBeanPostProcessor();
    }

    @Bean
    public RSocketServiceMeshInspector serviceMeshInspector(@Autowired RSocketBrokerProperties brokerConfig) {
        return new RSocketServiceMeshInspector(brokerConfig.isAuth());
    }

    @Bean
    public RSocketServiceRegistry rsocketServiceRegistry(@Autowired RSocketBrokerProperties brokerConfig,
                                                         @Autowired RSocketFilterChain chain,
                                                         @Autowired @Qualifier("notificationSink") Sinks.Many<String> notificationSink,
                                                         @Autowired AuthenticationService authenticationService,
                                                         @Autowired RSocketBrokerManager brokerManager,
                                                         @Autowired RSocketServiceMeshInspector serviceMeshInspector,
                                                         @Autowired(required = false) @Qualifier("upstreamBrokerCluster") UpstreamCluster upstreamBrokerCluster,
                                                         @Autowired ProviderRouter router,
                                                         @Autowired @Qualifier("p2pServiceNotificationSink") Sinks.Many<String> p2pServiceNotificationSink) {
        return new RSocketServiceRegistry(
                chain,
                notificationSink,
                authenticationService,
                brokerManager,
                serviceMeshInspector,
                brokerConfig.isAuth(),
                upstreamBrokerCluster,
                router,
                p2pServiceNotificationSink);
    }

    //----------------------------------------------broker binder相关----------------------------------------------
    @Bean(initMethod = "bind", destroyMethod = "dispose")
    public RSocketServer rsocketServer(@Autowired List<RSocketBinderCustomizer> customizers) {
        RSocketServer.Builder builder = RSocketServer.builder();
        customizers.forEach((customizer) -> customizer.customize(builder));
        return builder.build();
    }

    @Bean
    public RSocketBinderCustomizer rsocketBindCustomizer(@Autowired RSocketBrokerProperties brokerConfig,
                                                         @Autowired RSocketServiceRegistry serviceRegistry) {
        return builder -> {
            builder.acceptor(serviceRegistry.acceptor());
            builder.listen("tcp", brokerConfig.getPort());
        };
    }

    @Bean
    @ConditionalOnProperty(name = "kin.rsocket.broker.ssl.key-store")
    public RSocketBinderCustomizer rsocketSSLCustomizer(@Autowired ResourceLoader resourceLoader,
                                                        @Autowired RSocketBrokerProperties brokerConfig) {
        return builder -> {
            RSocketBrokerProperties.RSocketSSL rsocketSSL = brokerConfig.getSsl();
            if (rsocketSSL != null && rsocketSSL.isEnabled() && StringUtils.isNotBlank(rsocketSSL.getKeyStore())) {
                try {
                    KeyStore store = KeyStore.getInstance(rsocketSSL.getKeyStoreType());
                    char[] password = rsocketSSL.getKeyStorePassword().toCharArray();
                    //加载KeyStore
                    store.load(resourceLoader.getResource(rsocketSSL.getKeyStore()).getInputStream(), password);
                    String alias = store.aliases().nextElement();
                    //获取证书
                    Certificate certificate = store.getCertificate(alias);

                    //私钥
                    KeyStore.Entry priKeyEntry = store.getEntry(alias, new KeyStore.PasswordProtection(password));
                    PrivateKey privateKey = ((KeyStore.PrivateKeyEntry) priKeyEntry).getPrivateKey();
                    builder.sslContext(certificate, privateKey);
                    builder.listen("tcps", brokerConfig.getPort());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    //----------------------------------------------router----------------------------------------------
    @Bean("router")
    @ConditionalOnMissingBean
    @ConditionalOnExpression("'roundRobin'.equals('${kin.rsocket.broker.router}')")
    public ProviderRouter roundRobinRouter() {
        return new RoundRobinRouter();
    }

    @Bean("router")
    @ConditionalOnMissingBean
    @ConditionalOnExpression("'weightedStats'.equals('${kin.rsocket.broker.router}')")
    public ProviderRouter weightedStatsRouter() {
        return new WeightedStatsRouter();
    }

    /**
     * 配合{@link WeightedStatsRouter}使用, 获取可预测的rsocket request响应时间
     */
    @Bean
    @ConditionalOnExpression("'weightedStats'.equals('${kin.rsocket.broker.router}')")
    public RSocketBinderCustomizer weightedStatsInterceptorCustomizer(@Autowired WeightedStatsRouter router) {
        return builder -> builder.addRequesterRequestInterceptors(rsocket -> {
            WeightedStatsRequestInterceptor interceptor = new WeightedStatsRequestInterceptor() {
                @Override
                public void dispose() {
                    //移除监控信息
                    router.remove(rsocket);
                }
            };
            //绑定监控信息
            router.put(rsocket, interceptor);
            return interceptor;
        });
    }

    /**
     * 默认router, 故优先级最低
     * 因为定义得越后面, bean优先级越低, 故放在最后面即可
     */
    @Bean("router")
    @ConditionalOnMissingBean
    @ConditionalOnExpression("'random'.equals('${kin.rsocket.broker.router:random}')")
    public ProviderRouter randomRouter() {
        return new RandomRouter();
    }

    //----------------------------------------------upstream broker requester相关----------------------------------------------
    @Bean
    @ConditionalOnProperty(name = "kin.rsocket.broker.upstream-brokers")
    public UpstreamBrokerRequesterSupport upstreamBrokerRequesterSupport(@Autowired Environment env,
                                                                         @Autowired RSocketBrokerProperties brokerConfig,
                                                                         @Autowired RSocketServiceRegistry serviceRegistry,
                                                                         @Autowired RSocketFilterChain chain) {
        String appName = env.getProperty("spring.application.name", "unknown");
        return new UpstreamBrokerRequesterSupport(brokerConfig, appName, serviceRegistry, chain);
    }

    @Bean
    @ConditionalOnProperty(name = "kin.rsocket.broker.upstream-brokers")
    public UpstreamCluster upstreamBrokerCluster(@Autowired UpstreamBrokerRequesterSupport upStreamBrokerRequesterSupport,
                                                 @Autowired RSocketBrokerProperties brokerConfig) {
        return UpstreamCluster.brokerUpstreamCluster(upStreamBrokerRequesterSupport, brokerConfig.getUpstreamBrokers(), brokerConfig.getUpstreamLoadBalance());
    }

    //----------------------------------------------services----------------------------------------------
    @Bean
    public DiscoveryService discoveryService() {
        return new DiscoveryServiceImpl();
    }

    @Bean
    public HealthService healthService() {
        return new HealthService();
    }

    @Bean
    public CloudEventNotifyService cloudEventNotifyService() {
        return new CloudEventNotifyServiceImpl();
    }

    //----------------------------------------------controller----------------------------------------------
    @Bean
    public AppController appController() {
        return new AppController();
    }

    @Bean
    public BrokerClusterController brokerClusterController() {
        return new BrokerClusterController();
    }

    @Bean
    public RSocketApiController rsocketApiController() {
        return new RSocketApiController();
    }

    @Bean
    public RSocketServiceController rsocketServiceController() {
        return new RSocketServiceController();
    }

    public MetricsScrapeController metricsScrapeController() {
        return new MetricsScrapeController();
    }

    //----------------------------------------------metrics----------------------------------------------
    @Bean
    MeterRegistryCustomizer<MeterRegistry> defaultRegistryCustomizer(@Value("${spring.application.name}") String springAppName) {
        //根据应用区分监控指标
        return registry -> registry.config().commonTags("application", "kin-rsocket-broker-".concat(springAppName));
    }
}
