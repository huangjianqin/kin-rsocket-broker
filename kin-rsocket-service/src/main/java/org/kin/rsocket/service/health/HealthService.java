package org.kin.rsocket.service.health;

import org.kin.rsocket.core.RSocketService;
import org.kin.rsocket.core.health.HealthChecker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.boot.actuate.health.Status;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * @author huangjianqin
 * @date 2021/3/28
 */
@RSocketService(serviceInterface = HealthChecker.class)
public class HealthService implements HealthChecker {
    @Autowired
    private List<ReactiveHealthIndicator> healthIndicators;

    @Override
    public Mono<Integer> check(String serviceName) {
        return Flux.fromIterable(healthIndicators)
                .flatMap(healthIndicator -> healthIndicator
                        .health().map(Health::getStatus))
                .all(status -> status == Status.UP)
                .map(result -> result ? SERVING : DOWN);
    }
}
