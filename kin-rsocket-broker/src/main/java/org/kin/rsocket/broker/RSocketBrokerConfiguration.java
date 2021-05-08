package org.kin.rsocket.broker;

import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.auth.AuthenticationService;
import org.kin.rsocket.broker.cluster.BrokerManager;
import org.kin.rsocket.broker.cluster.StandAloneBrokerManager;
import org.kin.rsocket.broker.event.*;
import org.kin.rsocket.broker.services.BrokerDiscoveryService;
import org.kin.rsocket.broker.services.HealthService;
import org.kin.rsocket.core.*;
import org.kin.rsocket.core.discovery.DiscoveryService;
import org.kin.rsocket.core.event.CloudEventConsumer;
import org.kin.rsocket.core.event.CloudEventConsumers;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
import java.util.stream.Collectors;

/**
 * @author huangjianqin
 * @date 2021/2/15
 */
@Configuration
@EnableConfigurationProperties(RSocketBrokerProperties.class)
public class RSocketBrokerConfiguration {
    /**
     * 接受tips的flux
     */
    @Bean
    public Sinks.Many<String> notificationSink() {
        return Sinks.many().multicast().onBackpressureBuffer(8);
    }

    //----------------------------------------------cloud event consumers----------------------------------------------

    /**
     * 管理所有{@link CloudEventConsumer}的实例
     */
    @Bean(destroyMethod = "close")
    public CloudEventConsumers cloudEventConsumers(ObjectProvider<CloudEventConsumer> consumers) {
        CloudEventConsumers.INSTANCE.addConsumers(consumers.orderedStream().collect(Collectors.toList()));
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
    public ServicesExposedEventConsumer servicesExposedEventConsumer() {
        return new ServicesExposedEventConsumer();
    }

    @Bean
    public ServicesHiddenEventConsumer servicesHiddenEventConsumer() {
        return new ServicesHiddenEventConsumer();
    }

    @Bean
    @ConditionalOnBean(name = "upstreamBrokerCluster", value = UpstreamCluster.class)
    public UpstreamClusterChangedEventConsumer upstreamClusterChangedEventConsumer() {
        return new UpstreamClusterChangedEventConsumer();
    }

    @Bean
    public BrokerConfigChangedEventConsumer brokerConfigChangedEventConsumer() {
        return new BrokerConfigChangedEventConsumer();
    }

    @Bean
    public FilterEnableEventConsumer filterEnableEventConsumer() {
        return new FilterEnableEventConsumer();
    }

    //----------------------------------------------

    @Bean
    public RSocketFilterChain rsocketFilterChain(ObjectProvider<AbstractRSocketFilter> filters) {
        return new RSocketFilterChain(filters.orderedStream().collect(Collectors.toList()));
    }

    /**
     * {@link RSocketService}注解processor
     */
    @Bean
    public RSocketServiceAnnoProcessor rsocketServiceAnnoProcessor() {
        return new RSocketServiceAnnoProcessor();
    }

    @Bean
    public ServiceMeshInspector serviceMeshInspector(@Autowired RSocketBrokerProperties brokerConfig) {
        return new ServiceMeshInspector(brokerConfig.isAuth());
    }

    @Bean
    public ServiceManager serviceManager(@Autowired RSocketBrokerProperties brokerConfig,
                                         @Autowired RSocketFilterChain chain,
                                         @Autowired Sinks.Many<String> notificationSink,
                                         @Autowired AuthenticationService authenticationService,
                                         @Autowired BrokerManager brokerManager,
                                         @Autowired ServiceMeshInspector serviceMeshInspector,
                                         @Autowired(required = false) @Qualifier("upstreamBrokerCluster") UpstreamCluster upstreamBrokerCluster,
                                         @Autowired Router router) {
        return new ServiceManager(
                chain,
                notificationSink,
                authenticationService,
                brokerManager,
                serviceMeshInspector,
                brokerConfig.isAuth(),
                upstreamBrokerCluster,
                router);
    }

    /**
     * 默认{@link BrokerManager}实现, 可通过maven依赖配置其他starter来使用自定义{@link BrokerManager}实现
     */
    @Bean
    @ConditionalOnMissingBean(BrokerManager.class)
    public BrokerManager brokerManager() {
        return new StandAloneBrokerManager();
    }

    //----------------------------------------------broker binder相关----------------------------------------------
    @Bean(initMethod = "start", destroyMethod = "close")
    public RSocketBinder rsocketListener(ObjectProvider<RSocketBinderBuilderCustomizer> customizers) {
        RSocketBinder.Builder builder = RSocketBinder.builder();
        customizers.orderedStream().forEach((customizer) -> customizer.customize(builder));
        return builder.build();
    }

    @Bean
    public RSocketBinderBuilderCustomizer defaultRSocketListenerCustomizer(@Autowired RSocketBrokerProperties brokerConfig,
                                                                           @Autowired ServiceManager serviceManager) {
        return builder -> {
            builder.acceptor(serviceManager.acceptor());
            builder.listen("tcp", brokerConfig.getPort());
        };
    }

    @Bean
    @ConditionalOnProperty(name = "kin.rsocket.broker.ssl.key-store")
    public RSocketBinderBuilderCustomizer rsocketListenerSSLCustomizer(@Autowired ResourceLoader resourceLoader,
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
    public Router smoothWeightedRoundRobinRouter() {
        return new SmoothWeightedRoundRobinRouter();
    }

    /**
     * 默认router, 故优先级最低
     * 因为定义得越后面, bean优先级越低, 故放在最后面即可
     */
    @Bean("router")
    @ConditionalOnMissingBean
    @ConditionalOnExpression("'random'.equals('${kin.rsocket.broker.route:random}')")
    public Router weightedRandomRouter() {
        return new WeightedRandomRouter();
    }

    //----------------------------------------------upstream broker requester相关----------------------------------------------
    @Bean
    @ConditionalOnProperty(name = "kin.rsocket.broker.upstream-brokers")
    public UpstreamBrokerRequester upStreamBrokerRequester(@Autowired Environment env,
                                                           @Autowired RSocketBrokerProperties brokerConfig,
                                                           @Autowired ServiceManager serviceManager,
                                                           @Autowired RSocketFilterChain chain) {
        String appName = env.getProperty("spring.application.name", "unknown");
        return new UpstreamBrokerRequester(brokerConfig, appName, serviceManager, chain);
    }

    @Bean
    @ConditionalOnProperty(name = "kin.rsocket.broker.upstream-brokers")
    public UpstreamCluster upstreamBrokerCluster(@Autowired UpstreamBrokerRequester upStreamBrokerRequester,
                                                 @Autowired RSocketBrokerProperties brokerConfig) {
        return UpstreamCluster.brokerUpstreamCluster(upStreamBrokerRequester, brokerConfig.getUpstreamBrokers());
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
    //----------------------------------------------
}
