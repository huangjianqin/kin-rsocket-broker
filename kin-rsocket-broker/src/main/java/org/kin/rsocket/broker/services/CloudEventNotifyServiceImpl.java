package org.kin.rsocket.broker.services;

import org.kin.rsocket.broker.RSocketService;
import org.kin.rsocket.broker.RSocketServiceRegistry;
import org.kin.rsocket.core.event.CloudEventNotifyService;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * @author huangjianqin
 * @date 2021/8/11
 */
@org.kin.rsocket.core.RSocketService(CloudEventNotifyService.class)
public class CloudEventNotifyServiceImpl implements CloudEventNotifyService {
    @Autowired
    private RSocketServiceRegistry serviceRegistry;

    @Override
    public Mono<Void> notify(String appId, byte[] cloudEventBytes) {
        RSocketService rsocketService = serviceRegistry.getByUUID(appId);
        if (Objects.nonNull(rsocketService)) {
            return rsocketService.fireCloudEvent(cloudEventBytes);
        } else {
            return Mono.empty();
        }
    }

    @Override
    public Mono<Void> notifyAll(String appName, byte[] cloudEventBytes) {
        return Flux.fromIterable(serviceRegistry.getByAppName(appName))
                .flatMap(rs -> rs.fireCloudEvent(cloudEventBytes))
                .then();
    }
}
