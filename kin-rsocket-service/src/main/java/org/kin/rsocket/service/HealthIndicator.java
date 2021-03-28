package org.kin.rsocket.service;

import org.kin.rsocket.core.domain.AppStatus;
import org.kin.rsocket.core.health.HealthChecker;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import reactor.core.publisher.Mono;

/**
 * 启动health checker
 *
 * @author huangjianqin
 * @date 2021/3/28
 */
public class HealthIndicator implements ReactiveHealthIndicator {
    /** health checker */
    private HealthChecker healthChecker;
    /** rsocket service监控信息 */
    private RSocketEndpoint rsocketEndpoint;
    /** broker uris */
    private String brokerUris;

    public HealthIndicator(RSocketEndpoint rsocketEndpoint,
                           UpstreamClusterManager upstreamClusterManager,
                           String brokerUris) {
        this.rsocketEndpoint = rsocketEndpoint;
        this.healthChecker = ServiceReferenceBuilder
                .requester(HealthChecker.class)
                //todo 看看编码方式是否需要修改
                .nativeImage()
                .upstreamClusterManager(upstreamClusterManager)
                .build();
        this.brokerUris = brokerUris;
    }

    @Override
    public Mono<Health> health() {
        return healthChecker.check(null)
                .map(result -> {
                            boolean brokerAlive = result != null && result == 1;
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
