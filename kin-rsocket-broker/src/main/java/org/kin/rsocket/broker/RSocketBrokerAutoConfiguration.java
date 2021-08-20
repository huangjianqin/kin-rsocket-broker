package org.kin.rsocket.broker;

import io.micrometer.core.instrument.MeterRegistry;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.auth.AuthenticationService;
import org.kin.rsocket.broker.cluster.BrokerManager;
import org.kin.rsocket.broker.controller.*;
import org.kin.rsocket.broker.event.*;
import org.kin.rsocket.broker.services.BrokerDiscoveryService;
import org.kin.rsocket.broker.services.CloudEventNotifyServiceImpl;
import org.kin.rsocket.broker.services.HealthService;
import org.kin.rsocket.conf.ConfDiamond;
import org.kin.rsocket.core.*;
import org.kin.rsocket.core.discovery.DiscoveryService;
import org.kin.rsocket.core.event.CloudEventConsumer;
import org.kin.rsocket.core.event.CloudEventConsumers;
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
    @Bean(destroyMethod = "close")
    public CloudEventConsumers cloudEventConsumers(@Autowired List<CloudEventConsumer> consumers) {
        CloudEventConsumers.INSTANCE.addConsumers(consumers);
        return CloudEventConsumers.INSTANCE;
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
    @ConditionalOnBean(ConfDiamond.class)
    public BrokerConfigChangedEventConsumer brokerConfigChangedEventConsumer() {
        return new BrokerConfigChangedEventConsumer();
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
    public RSocketServiceAnnoProcessor rsocketServiceAnnoProcessor() {
        return new RSocketServiceAnnoProcessor();
    }

    @Bean
    public RSocketServiceMeshInspector serviceMeshInspector(@Autowired RSocketBrokerProperties brokerConfig) {
        return new RSocketServiceMeshInspector(brokerConfig.isAuth());
    }

    @Bean
    public RSocketServiceManager serviceManager(@Autowired RSocketBrokerProperties brokerConfig,
                                                @Autowired RSocketFilterChain chain,
                                                @Autowired @Qualifier("notificationSink") Sinks.Many<String> notificationSink,
                                                @Autowired AuthenticationService authenticationService,
                                                @Autowired BrokerManager brokerManager,
                                                @Autowired RSocketServiceMeshInspector serviceMeshInspector,
                                                @Autowired(required = false) @Qualifier("upstreamBrokerCluster") UpstreamCluster upstreamBrokerCluster,
                                                @Autowired ProviderRouter router,
                                                @Autowired @Qualifier("p2pServiceNotificationSink") Sinks.Many<String> p2pServiceNotificationSink) {
        return new RSocketServiceManager(
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
    @Bean(initMethod = "bind", destroyMethod = "close")
    public RSocketBinder rsocketBinder(@Autowired List<RSocketBinderCustomizer> customizers) {
        RSocketBinder.Builder builder = RSocketBinder.builder();
        customizers.forEach((customizer) -> customizer.customize(builder));
        return builder.build();
    }

    @Bean
    public RSocketBinderCustomizer defaultRSocketBinderCustomizer(@Autowired RSocketBrokerProperties brokerConfig,
                                                                  @Autowired RSocketServiceManager serviceManager) {
        return builder -> {
            builder.acceptor(serviceManager.acceptor());
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
                    //加载KeyStore
                    store.load(resourceLoader.getResource(rsocketSSL.getKeyStore()).getInputStream(), rsocketSSL.getKeyStorePassword().toCharArray());
                    String alias = store.aliases().nextElement();
                    //证书
                    Certificate certificate = store.getCertificate(alias);

                    KeyStore.Entry entry = store.getEntry(alias, new KeyStore.PasswordProtection(rsocketSSL.getKeyStorePassword().toCharArray()));
                    //私钥
                    PrivateKey privateKey = ((KeyStore.PrivateKeyEntry) entry).getPrivateKey();
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
    @ConditionalOnExpression("'roundRobin'.equals('${kin.rsocket.broker.route}')")
    public ProviderRouter smoothWeightedRoundRobinRouter() {
        return new SmoothWeightedRoundRobinRouter();
    }

    /**
     * 默认router, 故优先级最低
     * 因为定义得越后面, bean优先级越低, 故放在最后面即可
     */
    @Bean("router")
    @ConditionalOnMissingBean
    @ConditionalOnExpression("'random'.equals('${kin.rsocket.broker.route:random}')")
    public ProviderRouter weightedRandomRouter() {
        return new WeightedRandomRouter();
    }

    //----------------------------------------------upstream broker requester相关----------------------------------------------
    @Bean
    @ConditionalOnProperty(name = "kin.rsocket.broker.upstream-brokers")
    public UpstreamBrokerRequesterSupport upstreamBrokerRequesterSupport(@Autowired Environment env,
                                                                         @Autowired RSocketBrokerProperties brokerConfig,
                                                                         @Autowired RSocketServiceManager serviceManager,
                                                                         @Autowired RSocketFilterChain chain) {
        String appName = env.getProperty("spring.application.name", "unknown");
        return new UpstreamBrokerRequesterSupport(brokerConfig, appName, serviceManager, chain);
    }

    @Bean
    @ConditionalOnProperty(name = "kin.rsocket.broker.upstream-brokers")
    public UpstreamCluster upstreamBrokerCluster(@Autowired UpstreamBrokerRequesterSupport upStreamBrokerRequesterSupport,
                                                 @Autowired RSocketBrokerProperties brokerConfig) {
        return UpstreamCluster.brokerUpstreamCluster(upStreamBrokerRequesterSupport, brokerConfig.getUpstreamBrokers());
    }

    //----------------------------------------------services----------------------------------------------
    @Bean
    public DiscoveryService discoveryService() {
        return new BrokerDiscoveryService();
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
    public AppQueryController appQueryController() {
        return new AppQueryController();
    }

    @Bean
    @ConditionalOnBean(ConfDiamond.class)
    public ConfigController configController() {
        return new ConfigController();
    }

    @Bean
    public OprController oprController() {
        return new OprController();
    }

    @Bean
    public RSocketApiController rsocketApiController() {
        return new RSocketApiController();
    }

    @Bean
    public RSocketServiceQueryController serviceQueryController() {
        return new RSocketServiceQueryController();
    }

    //----------------------------------------------metrics----------------------------------------------
    @Bean
    MeterRegistryCustomizer<MeterRegistry> defaultRegistryCustomizer(@Value("${spring.application.name}") String springAppName) {
        //根据应用区分监控指标
        return registry -> registry.config().commonTags("application", "kin-rsocket-broker-".concat(springAppName));
    }

    //----------------------------------------------conf----------------------------------------------
    @Bean
    @ConditionalOnBean(ConfDiamond.class)
    public ConfWatcher confWatcher() {
        return new ConfWatcher();
    }

}
