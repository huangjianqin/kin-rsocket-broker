package org.kin.rsocket.broker.services;

import org.kin.rsocket.broker.ServiceManager;
import org.kin.rsocket.core.RSocketService;
import org.kin.rsocket.core.domain.AppStatus;
import org.kin.rsocket.core.health.HealthCheck;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author huangjianqin
 * @date 2021/3/29
 */
@RSocketService(HealthCheck.class)
public class HealthService implements HealthCheck {
    /** 服务实例路由表 */
    @Autowired
    private ServiceManager serviceManager;

    @Override
    public Mono<Integer> check(String serviceName) {
        //health check
        if (serviceName == null || serviceName.isEmpty()) {
            //broker心跳
            return Mono.just(AppStatus.SERVING.getId());
        }

        //指定服务的health check
        return Flux.fromIterable(serviceManager.getAllServices())
                .any(serviceLocator -> serviceLocator.getService().equals(serviceName))
                .map(result -> result ? AppStatus.SERVING.getId() : AppStatus.DOWN.getId());
    }
}
