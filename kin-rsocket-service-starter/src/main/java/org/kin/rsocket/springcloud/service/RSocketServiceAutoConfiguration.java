package org.kin.rsocket.springcloud.service;

import org.kin.rsocket.core.RSocketAppContext;
import org.kin.rsocket.core.RSocketService;
import org.kin.rsocket.core.RSocketServiceAnnoProcessor;
import org.kin.rsocket.core.health.HealthCheck;
import org.kin.rsocket.service.RSocketServiceConnector;
import org.kin.rsocket.service.ServiceReferenceBuilder;
import org.kin.rsocket.springcloud.service.event.CloudEvent2ApplicationEventConsumer;
import org.kin.rsocket.springcloud.service.event.InvalidCacheEventConsumer;
import org.kin.rsocket.springcloud.service.health.HealthIndicator;
import org.kin.rsocket.springcloud.service.health.HealthService;
import org.kin.rsocket.springcloud.service.health.RSocketEndpoint;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

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

    @Bean(autowireCandidate = false)
    public CloudEvent2ApplicationEventConsumer cloudEvent2ListenerConsumer() {
        return new CloudEvent2ApplicationEventConsumer();
    }

    @Bean(autowireCandidate = false)
    public InvalidCacheEventConsumer invalidCacheEventConsumer() {
        return new InvalidCacheEventConsumer();
    }

    //--------------------------------------------------------------------------------
    @Bean(destroyMethod = "close")
    public RSocketServiceConnector rsocketServiceConnector(@Autowired Environment env) {
        String appName = env.getProperty("spring.application.name", "unknown");
        return new RSocketServiceConnector(appName, config);
    }
    //----------------------------spring----------------------------

    /**
     * {@link RSocketService}注解processor
     */
    @Bean
    public RSocketServiceAnnoProcessor rsocketServiceAnnoProcessor() {
        return new RSocketServiceAnnoProcessor(config.getGroup(), config.getVersion());
    }

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
    @Bean(autowireCandidate = false)
    public RSocketEndpoint rsocketEndpoint() {
        return new RSocketEndpoint(config, rsocketServiceConnector(null));
    }

    /**
     * broker health checker
     */
    @Bean
    @ConditionalOnProperty("kin.rsocket.brokers")
    public HealthIndicator healthIndicator(@Value("${kin.rsocket.brokers}") String brokers) {
        return new HealthIndicator(rsocketEndpoint(), healthCheckRef(), brokers);
    }

    /**
     * 自带的health checker rsocket service
     */
    @Bean
    public HealthService healthService(@Autowired ObjectProvider<ReactiveHealthIndicator> healthIndicators) {
        return new HealthService(healthIndicators.orderedStream().collect(Collectors.toList()));
    }

    /**
     * 用于初始化{@link RSocketAppContext}端口赋值
     */
    @SuppressWarnings("ConstantConditions")
    @Bean
    public ApplicationListener<WebServerInitializedEvent> webServerInitializedEventApplicationListener(@Autowired Environment environment) {
        return webServerInitializedEvent -> {
            //namespace 是由开发者自定义的
            String namespace = webServerInitializedEvent.getApplicationContext().getServerNamespace();
            int listenPort = webServerInitializedEvent.getWebServer().getPort();
            if ("management".equals(namespace)) {
                RSocketAppContext.managementPort = listenPort;
            } else {
                RSocketAppContext.webPort = listenPort;
                if (environment.containsProperty("management.server.port")) {
                    RSocketAppContext.managementPort = environment.getProperty("management.server.port", Integer.class);
                }
            }
        };
    }

    //----------------------------service reference----------------------------
    @Bean(autowireCandidate = false)
    public HealthCheck healthCheckRef() {
        return ServiceReferenceBuilder
                .requester(HealthCheck.class)
                //todo 看看编码方式是否需要修改
                .nativeImage()
                .upstreamClusterManager(rsocketServiceConnector(null))
                .build();
    }
}
