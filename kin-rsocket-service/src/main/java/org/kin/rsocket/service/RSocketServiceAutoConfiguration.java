package org.kin.rsocket.service;

import io.rsocket.SocketAcceptor;
import org.kin.framework.utils.ExceptionUtils;
import org.kin.rsocket.core.*;
import org.kin.rsocket.core.event.CloudEventConsumer;
import org.kin.rsocket.core.event.CloudEventConsumers;
import org.kin.rsocket.core.health.HealthCheck;
import org.kin.rsocket.core.utils.Symbols;
import org.kin.rsocket.service.event.CloudEvent2ApplicationEventConsumer;
import org.kin.rsocket.service.event.InvalidCacheEventConsumer;
import org.kin.rsocket.service.event.UpstreamClusterChangedEventConsumer;
import org.kin.rsocket.service.health.HealthIndicator;
import org.kin.rsocket.service.health.HealthService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

/**
 * @author huangjianqin
 * @date 2021/3/28
 */
@Configuration
@EnableConfigurationProperties(RSocketServiceProperties.class)
public class RSocketServiceAutoConfiguration {
    @Autowired
    private RSocketServiceProperties config;

    //----------------------------cloud event consumers----------------------------

    /**
     * 管理所有{@link CloudEventConsumer}的实例
     */
    @Bean
    public CloudEventConsumers cloudEventConsumers(ObjectProvider<CloudEventConsumer> customConsumers) {
        CloudEventConsumers.INSTANCE.addConsumers(customConsumers.stream().collect(Collectors.toList()));
        return CloudEventConsumers.INSTANCE;
    }

    @Bean
    public UpstreamClusterChangedEventConsumer upstreamClusterChangedEventConsumer(@Autowired UpstreamClusterManager upstreamClusterManager) {
        return new UpstreamClusterChangedEventConsumer(upstreamClusterManager);
    }

    @Bean
    public CloudEvent2ApplicationEventConsumer cloudEventToListenerConsumer() {
        return new CloudEvent2ApplicationEventConsumer();
    }

    @Bean
    public InvalidCacheEventConsumer invalidCacheEventConsumer() {
        return new InvalidCacheEventConsumer();
    }

    //--------------------------------------------------------------------------------

    /**
     * 默认服务注册中心
     */
    @Bean
    @ConditionalOnMissingBean(ReactiveServiceRegistry.class)
    public ReactiveServiceRegistry serviceRegistry() {
        return new DefaultServiceRegistry();
    }

    /**
     * responder acceptor factory
     */
    @Bean
    public SocketAcceptor responderAcceptorFactory(@Autowired ReactiveServiceRegistry serviceRegistry) {
        return (setupPayload, requester) -> Mono.fromCallable(() -> new Responder(serviceRegistry, requester, setupPayload));
    }

    @Bean
    @ConditionalOnMissingBean(RequesterSupport.class)
    public RequesterSupport requesterSupport(@Autowired Environment environment,
                                             @Autowired ReactiveServiceRegistry serviceRegistry,
                                             @Autowired SocketAcceptor socketAcceptor,
                                             @Autowired ObjectProvider<RequesterSupportBuilderCustomizer> customizers) {
        RequesterSupportBuilder builder = RequesterSupportBuilder.builder(config, environment, serviceRegistry, socketAcceptor);
        customizers.orderedStream().forEach((customizer) -> customizer.customize(builder));
        return builder.build();
    }

    /**
     * {@link RSocketService}注解processor
     */
    @Bean
    public RSocketServiceAnnoProcessor rsocketServiceAnnoProcessor(@Autowired ReactiveServiceRegistry serviceRegistry) {
        return new RSocketServiceAnnoProcessor(config.getGroup(), config.getVersion(), serviceRegistry);
    }

    /**
     * upstream cluster manager
     */
    @Bean(destroyMethod = "close")
    public UpstreamClusterManager upstreamClusterManager(@Autowired RequesterSupport requesterSupport) throws JwtTokenNotFoundException {
        UpstreamClusterManager upstreamClusterManager = new UpstreamClusterManager(requesterSupport);
        //init
        if (config.getBrokers() != null && !config.getBrokers().isEmpty()) {
            if (config.getJwtToken() == null || config.getJwtToken().isEmpty()) {
                try {
                    throw new JwtTokenNotFoundException();
                } catch (JwtTokenNotFoundException e) {
                    ExceptionUtils.throwExt(e);
                }
            }
            upstreamClusterManager.add(null, Symbols.BROKER, null, config.getBrokers());
        }
        if (config.getEndpoints() != null && !config.getEndpoints().isEmpty()) {
            for (EndpointProperties endpointProperties : config.getEndpoints()) {
                upstreamClusterManager.add(
                        endpointProperties.getGroup(),
                        endpointProperties.getService(),
                        endpointProperties.getVersion(),
                        endpointProperties.getUris());
            }
        }
        return upstreamClusterManager;
    }

    //----------------------------spring----------------------------
    /**
     * 服务暴露给broker逻辑实现
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public ServicesPublisher servicesPublisher() {
        return new ServicesPublisher();
    }

    /**
     * 开启actuator监控
     */
    @Bean
    public RSocketEndpoint rsocketEndpoint(@Autowired UpstreamClusterManager upstreamClusterManager,
                                           @Autowired RequesterSupport requesterSupport) {
        return new RSocketEndpoint(config, upstreamClusterManager, requesterSupport);
    }

    /**
     * broker health checker
     */
    @Bean
    @ConditionalOnProperty("kin.rsocket.brokers")
    public HealthIndicator healthIndicator(@Autowired RSocketEndpoint rsocketEndpoint,
                                           @Autowired @Qualifier("healthCheckRef") HealthCheck healthCheck,
                                           @Value("${kin.rsocket.brokers}") String brokers) {
        return new HealthIndicator(rsocketEndpoint, healthCheck, brokers);
    }

    /**
     * 自带的health checker rsocket service
     */
    @Bean
    @ConditionalOnMissingBean
    public HealthService healthService() {
        return new HealthService();
    }

    /**
     * 用于初始化{@link RSocketAppContext}端口赋值
     */
    @SuppressWarnings("ConstantConditions")
    @Bean
    public ApplicationListener<WebServerInitializedEvent> webServerInitializedEventApplicationListener(@Autowired Environment environment) {
        return webServerInitializedEvent -> {
            String namespace = webServerInitializedEvent.getApplicationContext().getServerNamespace();
            int listenPort = webServerInitializedEvent.getWebServer().getPort();
            if ("management".equals(namespace)) {
                RSocketAppContext.managementPort = listenPort;
            } else {
                RSocketAppContext.webPort = listenPort;
                if (environment.getProperty("management.server.port", Integer.class) == 0) {
                    RSocketAppContext.managementPort = listenPort;
                }
            }
        };
    }

    //----------------------------service reference----------------------------
    @Bean
    public HealthCheck healthCheckRef(@Autowired UpstreamClusterManager upstreamClusterManager) {
        return ServiceReferenceBuilder
                .requester(HealthCheck.class)
                //todo 看看编码方式是否需要修改
                .nativeImage()
                .upstreamClusterManager(upstreamClusterManager)
                .build();
    }
}
