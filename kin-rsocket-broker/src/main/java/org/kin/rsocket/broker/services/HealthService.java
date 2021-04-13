package org.kin.rsocket.broker.services;

import org.kin.rsocket.broker.ServiceRouteTable;
import org.kin.rsocket.core.RSocketService;
import org.kin.rsocket.core.domain.AppStatus;
import org.kin.rsocket.core.health.HealthChecker;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author huangjianqin
 * @date 2021/3/29
 */
@RSocketService(HealthChecker.class)
public class HealthService implements HealthChecker {
    /** 服务实例路由表 */
    private ServiceRouteTable routeTable;

    public HealthService(ServiceRouteTable routeTable) {
        this.routeTable = routeTable;
    }

    @Override
    public Mono<Integer> check(String serviceName) {
        //health check
        if (serviceName == null || serviceName.isEmpty()) {
            return Mono.just(AppStatus.SERVING.getId());
        }

        return Flux.fromIterable(routeTable.getAllServices())
                .any(serviceLocator -> serviceLocator.getService().equals(serviceName))
                .map(result -> result ? AppStatus.SERVING.getId() : AppStatus.DOWN.getId());
    }
}
