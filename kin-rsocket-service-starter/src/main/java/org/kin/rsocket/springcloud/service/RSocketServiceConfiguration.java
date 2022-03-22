package org.kin.rsocket.springcloud.service;

import brave.Tracing;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.kin.rsocket.core.*;
import org.kin.rsocket.core.health.HealthCheck;
import org.kin.rsocket.service.RSocketRequesterSupportCustomizer;
import org.kin.rsocket.service.RSocketServiceRequester;
import org.kin.rsocket.service.health.BrokerHealthCheckReference;
import org.kin.rsocket.springcloud.service.health.HealthIndicator;
import org.kin.rsocket.springcloud.service.health.HealthService;
import org.kin.rsocket.springcloud.service.health.RSocketEndpoint;
import org.kin.rsocket.springcloud.service.metrics.MetricsServicePrometheusImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * @author huangjianqin
 * @date 2021/3/28
 */
@Configuration
@EnableConfigurationProperties(RSocketServiceProperties.class)
@Import(RSocketCloudEventConsumerConfiguration.class)
public class RSocketServiceConfiguration {
    @Bean(destroyMethod = "close")
    public RSocketServiceRequester rsocketServiceRequester(@Autowired Environment env,
                                                           @Autowired RSocketServiceProperties rsocketServiceProperties,
                                                           @Autowired List<RSocketBinderCustomizer> binderCustomizers,
                                                           @Autowired List<RSocketRequesterSupportCustomizer> requesterSupportCustomizers,
                                                           @Autowired(required = false) HealthService healthService,
                                                           @Autowired(required = false) Tracing tracing) {
        String appName = env.getProperty("spring.application.name", "unknown");
        RSocketServiceRequester.Builder builder = RSocketServiceRequester.builder(appName, rsocketServiceProperties)
                .binderCustomizers(binderCustomizers)
                .requesterSupportBuilderCustomizers(requesterSupportCustomizers)
                .healthCheck(healthService);
        if (Objects.nonNull(tracing)) {
            builder.tracer(tracing.tracer());
        }

        return builder.build();
    }

    //----------------------------spring----------------------------

    /**
     * {@link RSocketService}注解processor
     */
    @Bean
    public RSocketServiceAnnotationBeanPostProcessor rsocketServiceAnnotationBeanPostProcessor(@Autowired RSocketServiceProperties rsocketServiceProperties) {
        return new RSocketServiceAnnotationBeanPostProcessor(rsocketServiceProperties.getGroup(), rsocketServiceProperties.getVersion());
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
    public RSocketEndpoint rsocketEndpoint(@Autowired RSocketServiceProperties rsocketServiceProperties,
                                           @Autowired RSocketServiceRequester requester) {
        return new RSocketEndpoint(rsocketServiceProperties, requester);
    }

    /**
     * broker health checker
     */
    @Bean
    @ConditionalOnProperty("kin.rsocket.brokers")
    public HealthIndicator healthIndicator(@Autowired RSocketServiceProperties rsocketServiceProperties,
                                           @Autowired RSocketEndpoint endpoint,
                                           @Autowired @Qualifier("healthCheckRef") HealthCheck healthCheck) {
        return new HealthIndicator(endpoint, healthCheck, StringUtils.collectionToCommaDelimitedString(rsocketServiceProperties.getBrokers()));
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

    //----------------------------internal service----------------------------

    /**
     * 自带的health checker rsocket service
     */
    @Bean
    public HealthService healthService() {
        return new HealthService();
    }

    @Bean
    @ConditionalOnBean(PrometheusMeterRegistry.class)
    public MetricsService metricsService() {
        return new MetricsServicePrometheusImpl();
    }

    //----------------------------service reference----------------------------

    /**
     * 独立出来bean, 是为了让{@link HealthIndicator}引用到
     */
    @Bean("healthCheckRef")
    public HealthCheck healthCheckRef(@Autowired RSocketServiceRequester requester) {
        return new BrokerHealthCheckReference(requester);
    }
}
