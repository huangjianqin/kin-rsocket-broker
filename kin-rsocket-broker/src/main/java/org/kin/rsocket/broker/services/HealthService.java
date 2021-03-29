package org.kin.rsocket.broker.services;

import org.kin.rsocket.core.RSocketService;
import org.kin.rsocket.core.domain.AppStatus;
import org.kin.rsocket.core.health.HealthChecker;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author huangjianqin
 * @date 2021/3/29
 */
@RSocketService(serviceInterface = HealthChecker.class)
public class HealthService implements HealthChecker {
    /**
     *
     */
    private ServiceRoutingSelector routingSelector;

    public HealthService(ServiceRoutingSelector routingSelector) {
        this.routingSelector = routingSelector;
    }

    @Override
    public Mono<Integer> check(String serviceName) {
        //health check
        if (serviceName == null || serviceName.isEmpty()) {
            return Mono.just(AppStatus.SERVING.getId());
        }

        return Flux.fromIterable(routingSelector.findAllServices())
                .any(serviceLocator -> serviceLocator.getService().equals(serviceName))
                .map(result -> result ? AppStatus.SERVING.getId() : AppStatus.DOWN.getId());
    }
}
