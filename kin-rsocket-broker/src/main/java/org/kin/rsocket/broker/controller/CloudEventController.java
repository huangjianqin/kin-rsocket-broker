package org.kin.rsocket.broker.controller;

import org.kin.rsocket.core.event.CloudEventBus;
import org.kin.rsocket.core.utils.JSON;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * cloud event相关接口
 *
 * @author huangjianqin
 * @date 2022/12/7
 */
@RestController
@RequestMapping("/cloudEvent")
public class CloudEventController {
    @PostMapping("/post")
    public Mono<Void> postCloudEvent(@RequestBody byte[] cloudEventBytes) {
        return Mono.fromRunnable(() -> CloudEventBus.INSTANCE.postCloudEvent(JSON.deserializeCloudEvent(cloudEventBytes)));
    }
}
