package org.kin.rsocket.springcloud.service.health;

import org.kin.rsocket.core.RSocketService;
import org.kin.rsocket.core.health.HealthCheck;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author huangjianqin
 * @date 2021/3/28
 */
@RSocketService(HealthCheck.class)
public final class HealthService implements HealthCheck, ApplicationListener<ContextRefreshedEvent> {
    /**
     * 使用{@link ApplicationListener}为了解决循环依赖
     * {@link org.kin.rsocket.service.RSocketServiceRequester} ----> {@link HealthService} ----> {@link HealthIndicator} ----> {@link RSocketEndpoint}
     * ^                                                                                                              |
     * |                                                                                                              |
     * |                                                                                                              |
     * <--------------------------------------------------------------------------------------------------------------|
     */
    private volatile List<ReactiveHealthIndicator> healthIndicators = Collections.emptyList();

    @Override
    public Mono<Integer> check(String serviceName) {
        return Flux.fromIterable(healthIndicators)
                .flatMap(healthIndicator -> healthIndicator
                        .health().map(Health::getStatus))
                .all(status -> status == Status.UP)
                .map(result -> result ? SERVING : DOWN);
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        ApplicationContext context = event.getApplicationContext();
        healthIndicators = Collections.unmodifiableList(new ArrayList<>(context.getBeansOfType(ReactiveHealthIndicator.class).values()));
    }
}
