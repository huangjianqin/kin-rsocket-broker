package org.kin.rsocket.springcloud.service.health;

import org.kin.rsocket.core.domain.AppStatus;
import org.kin.rsocket.core.health.HealthCheck;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import reactor.core.publisher.Mono;

/**
 * 启动health checker
 * 聚合broker状态和服务自身endpoint状态, 来决定health状态
 *
 * @author huangjianqin
 * @date 2021/3/28
 */
public final class HealthIndicator implements ReactiveHealthIndicator {
    /** health checker */
    private final HealthCheck healthCheck;
    /** rsocket service监控信息 */
    private final RSocketEndpoint rsocketEndpoint;
    /** broker uris */
    private final String brokerUris;

    public HealthIndicator(RSocketEndpoint rsocketEndpoint,
                           HealthCheck healthCheck,
                           String brokerUris) {
        this.healthCheck = healthCheck;
        this.rsocketEndpoint = rsocketEndpoint;
        this.brokerUris = brokerUris;
    }

    @Override
    public Mono<Health> health() {
        return healthCheck.check(null)
                .map(result -> {
                            boolean brokerAlive = result != null && result == 1;
                    //endpoint health status
                            AppStatus serviceStatus = rsocketEndpoint.getServiceStatus();
                            boolean localServicesAlive = !serviceStatus.equals(AppStatus.STOPPED);
                            Health.Builder builder = brokerAlive && localServicesAlive ? Health.up() : Health.outOfService();
                            builder.withDetail("brokers", brokerUris);
                            builder.withDetail("localServiceStatus", serviceStatus.getDesc());
                            return builder.build();
                        }
                )
                .onErrorReturn(Health.down().withDetail("brokers", brokerUris).build());
    }

}
