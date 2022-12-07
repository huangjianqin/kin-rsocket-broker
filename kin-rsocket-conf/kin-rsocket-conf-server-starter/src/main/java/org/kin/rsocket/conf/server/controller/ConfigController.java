package org.kin.rsocket.conf.server.controller;

import org.kin.rsocket.conf.ConfDiamond;
import org.kin.rsocket.core.event.CloudEventNotifyService;
import org.kin.rsocket.core.event.ConfigChangedEvent;
import org.kin.rsocket.core.utils.JSON;
import org.kin.rsocket.service.RSocketServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * @author huangjianqin
 * @date 2021/3/30
 */
@RestController
@RequestMapping("/config")
public class ConfigController {
    private static final Logger log = LoggerFactory.getLogger(ConfigController.class);
    private static final String KEY_APPLICATION_CONFIGURATION = "application.properties";

    @Autowired
    private ConfDiamond confDiamond;
    @RSocketServiceReference
    private CloudEventNotifyService cloudEventNotifyService;

    /**
     * 刷新指定app所有实例应用配置
     */
    @PostMapping("/refresh/{appName}")
    public Mono<Void> refresh(@PathVariable(name = "appName") String appName) {
        return confDiamond.get(appName, KEY_APPLICATION_CONFIGURATION)
                .map(content -> ConfigChangedEvent.of(appName, content))
                .flatMap(event -> cloudEventNotifyService.notifyAll(appName, JSON.serializeCloudEvent(event.toCloudEvent())));
    }

    /**
     * 刷新指定app实例应用配置
     */
    @PostMapping("/refresh/{appName}/{appId}")
    public Mono<Void> refresh(@PathVariable(name = "appName") String appName,
                              @PathVariable(name = "appId") String appId) {
        return confDiamond.get(appName, KEY_APPLICATION_CONFIGURATION)
                .map(content -> ConfigChangedEvent.of(appName, content))
                .flatMap(event -> cloudEventNotifyService.notify(appId, JSON.serializeCloudEvent(event.toCloudEvent())));
    }

    /**
     * 更新并刷新app实例应用配置
     */
    @PostMapping("/updateRefresh/{appName}")
    public Mono<Void> updateRefresh(@PathVariable(name = "appName") String appName,
                                    @RequestBody String body) {
        return update(appName, KEY_APPLICATION_CONFIGURATION, body).flatMap(v -> refresh(appName));
    }

    /**
     * 更新并刷新指定app实例应用配置
     */
    @PostMapping("/updateRefresh/{appName}/{appId}")
    public Mono<Void> updateRefresh(@PathVariable(name = "appName") String appName,
                                    @PathVariable(name = "appId") String appId,
                                    @RequestBody String body) {
        return update(appName, KEY_APPLICATION_CONFIGURATION, body).flatMap(v -> refresh(appName, appId));
    }

    /**
     * 更新配置
     */
    @PostMapping("/update/{appName}/{key}")
    public Mono<Void> update(@PathVariable(name = "appName") String appName,
                             @PathVariable(name = "key") String key,
                             @RequestBody String body) {
        if (key.contains(ConfDiamond.GROUP_KEY_SEPARATOR)) {
            return Mono.error(new IllegalArgumentException(String.format("key is not support to have char '%s'", ConfDiamond.GROUP_KEY_SEPARATOR)));
        }

        return Mono.just(body).flatMap(b -> confDiamond.put(appName, key, b));
    }

    @GetMapping("/last/{appName}/{key}")
    public Mono<String> fetch(@PathVariable(name = "appName") String appName,
                              @PathVariable(name = "key") String key) {
        return confDiamond.get(appName, key);
    }
}
