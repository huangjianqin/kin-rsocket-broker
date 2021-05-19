package org.kin.rsocket.springcloud.service;

import org.kin.rsocket.core.RSocketAppContext;
import org.kin.rsocket.core.RSocketService;
import org.kin.rsocket.core.RSocketServiceAnnoProcessor;
import org.kin.rsocket.core.health.HealthCheck;
import org.kin.rsocket.service.RSocketServiceConnector;
import org.kin.rsocket.service.RSocketServiceReferenceBuilder;
import org.kin.rsocket.springcloud.service.health.HealthIndicator;
import org.kin.rsocket.springcloud.service.health.HealthService;
import org.kin.rsocket.springcloud.service.health.RSocketEndpoint;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import java.util.stream.Collectors;

/**
 * @author huangjianqin
 * @date 2021/3/28
 */
@Configuration
@EnableConfigurationProperties(RSocketServiceProperties.class)
@Import(RSocketCloudEventConsumerConfiguration.class)
public class RSocketServiceConfiguration {
    @Bean(destroyMethod = "close")
    public RSocketServiceConnector rsocketServiceConnector(@Autowired Environment env,
                                                           @Autowired RSocketServiceProperties config) {
        String appName = env.getProperty("spring.application.name", "unknown");
        return new RSocketServiceConnector(appName, config);
    }

    //----------------------------spring----------------------------
    /**
     * {@link RSocketService}注解processor
     * 此处使用{@link Value}而不是{@link Autowired} {@link RSocketServiceProperties}, 是为了先初始化{@link RSocketServiceAnnoProcessor},
     * 避免{@link RSocketServiceProperties}没有被所有{@link org.springframework.beans.factory.config.BeanPostProcessor}处理, 而导致spring打印警告
     */
    @Bean
    public RSocketServiceAnnoProcessor rsocketServiceAnnoProcessor(@Value("${kin.rsocket.group:}") String group,
                                                                   @Value("${kin.rsocket.version:}") String version) {
        return new RSocketServiceAnnoProcessor(group, version);
    }

    /**
     * 服务暴露给broker逻辑实现
     */
    @Bean
    public RSocketServicesPublisher servicesPublisher() {
        return new RSocketServicesPublisher();
    }

    /**
     * 开启actuator监控
     */
    @Bean
    public RSocketEndpoint rsocketEndpoint(@Autowired RSocketServiceProperties config,
                                           @Autowired RSocketServiceConnector connector) {
        return new RSocketEndpoint(config, connector);
    }

    /**
     * broker health checker
     */
    @Bean
    @ConditionalOnProperty("kin.rsocket.brokers")
    public HealthIndicator healthIndicator(@Value("${kin.rsocket.brokers}") String brokers,
                                           @Autowired RSocketEndpoint endpoint,
                                           @Autowired @Qualifier("healthCheckRef") HealthCheck healthCheck) {
        return new HealthIndicator(endpoint, healthCheck, brokers);
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

    @Bean
    public JwtTokenFailureAnalyzer jwtTokenFailureAnalyzer() {
        return new JwtTokenFailureAnalyzer();
    }

    //----------------------------service reference----------------------------
    @Bean("healthCheckRef")
    public HealthCheck healthCheckRef(@Autowired RSocketServiceConnector connector) {
        return RSocketServiceReferenceBuilder
                .requester(HealthCheck.class)
                .nativeImage()
                .upstreamClusterManager(connector)
                .build();
    }
}
