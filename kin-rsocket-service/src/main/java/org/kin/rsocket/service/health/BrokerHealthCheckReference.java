package org.kin.rsocket.service.health;

import org.kin.rsocket.core.domain.AppStatus;
import org.kin.rsocket.core.health.HealthCheck;
import org.kin.rsocket.service.RSocketServiceReferenceBuilder;
import org.kin.rsocket.service.UpstreamClusterManager;
import reactor.core.publisher.Mono;

/**
 * 内置health check, 只要broker正常, 本app就可以对外提供服务
 *
 * @author huangjianqin
 * @date 2022/1/16
 */
public final class BrokerHealthCheckReference implements HealthCheck {
    /** broker health check */
    private final HealthCheck reference;

    public BrokerHealthCheckReference(UpstreamClusterManager upstreamClusterManager) {
        reference = RSocketServiceReferenceBuilder
                .requester(HealthCheck.class)
                .nativeImage()
                .upstreamClusterManager(upstreamClusterManager)
                .build();
    }

    @Override
    public Mono<Integer> check(String service) {
        return reference.check(null).map(r -> AppStatus.SERVING.getId() == r ? 1 : 0);
    }
}
