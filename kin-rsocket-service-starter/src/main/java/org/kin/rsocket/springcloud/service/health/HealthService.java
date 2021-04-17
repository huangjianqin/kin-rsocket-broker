package org.kin.rsocket.springcloud.service.health;

import org.kin.rsocket.core.RSocketService;
import org.kin.rsocket.core.health.HealthCheck;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.boot.actuate.health.Status;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * todo 单机与spring模式需要使用不同的HealthService
 *
 * @author huangjianqin
 * @date 2021/3/28
 */
@RSocketService(HealthCheck.class)
public final class HealthService implements HealthCheck {
    private final List<ReactiveHealthIndicator> healthIndicators;

    public HealthService(List<ReactiveHealthIndicator> healthIndicators) {
        this.healthIndicators = healthIndicators;
    }

    @Override
    public Mono<Integer> check(String serviceName) {
        return Flux.fromIterable(healthIndicators)
                .flatMap(healthIndicator -> healthIndicator
                        .health().map(Health::getStatus))
                .all(status -> status == Status.UP)
                .map(result -> result ? SERVING : DOWN);
    }
}
