package org.kin.rsocket.springcloud.service.health;

import org.kin.rsocket.core.RSocketService;
import org.kin.rsocket.core.health.HealthCheck;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.context.annotation.Lazy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * @author huangjianqin
 * @date 2021/3/28
 */
@RSocketService(HealthCheck.class)
public final class HealthService implements HealthCheck {
    /**
     * 使用延迟加载, 为了解决循环依赖
     * {@link org.kin.rsocket.service.RSocketServiceRequester} ----> {@link HealthService} ----> {@link HealthIndicator} ----> {@link RSocketEndpoint}
     * ^                                                                                                              |
     * |                                                                                                              |
     * |                                                                                                              |
     * <--------------------------------------------------------------------------------------------------------------|
     */
    @Lazy
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
