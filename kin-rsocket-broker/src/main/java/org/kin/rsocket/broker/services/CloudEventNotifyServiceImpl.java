package org.kin.rsocket.broker.services;

import org.kin.rsocket.broker.BrokerResponder;
import org.kin.rsocket.broker.RSocketServiceManager;
import org.kin.rsocket.core.RSocketService;
import org.kin.rsocket.core.event.CloudEventNotifyService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.util.Objects;

/**
 * @author huangjianqin
 * @date 2021/8/11
 */
@RSocketService(CloudEventNotifyService.class)
public class CloudEventNotifyServiceImpl implements CloudEventNotifyService {
    @Resource
    private RSocketServiceManager serviceManager;

    @Override
    public Mono<Void> notify(String appId, String cloudEventJson) {
        BrokerResponder responder = serviceManager.getByUUID(appId);
        if (Objects.nonNull(responder)) {
            return responder.fireCloudEvent(cloudEventJson);
        } else {
            return Mono.empty();
        }
    }

    @Override
    public Mono<Void> notifyAll(String appName, String cloudEventJson) {
        return Flux.fromIterable(serviceManager.getByAppName(appName))
                .flatMap(responderHandler -> responderHandler.fireCloudEvent(cloudEventJson))
                .then();
    }
}
