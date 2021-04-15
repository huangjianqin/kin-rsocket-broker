package org.kin.rsocket.broker.controller;

import io.rsocket.exceptions.InvalidException;
import org.kin.rsocket.auth.AuthenticationService;
import org.kin.rsocket.broker.RSocketBrokerProperties;
import org.kin.rsocket.broker.ServiceManager;
import org.kin.rsocket.conf.ConfDiamond;
import org.kin.rsocket.core.RSocketAppContext;
import org.kin.rsocket.core.event.CloudEventBuilder;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.ConfigChangedEvent;
import org.kin.rsocket.core.metadata.AppMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * todo 一些常量看看需不需要修改
 *
 * @author huangjianqin
 * @date 2021/3/30
 */
@RestController
@RequestMapping("/config")
public class ConfigController {
    @Autowired
    private ConfDiamond confDiamond;
    @Autowired
    private AuthenticationService authenticationService;
    @Autowired
    private ServiceManager serviceManager;
    @Autowired
    private RSocketBrokerProperties brokerConfig;

    @PostMapping("/refresh/{appName}")
    public Mono<String> refresh(@PathVariable(name = "appName") String appName,
                                @RequestParam(name = "ip", required = false) String ip,
                                @RequestParam(name = "id", required = false) String id,
                                @RequestHeader(name = HttpHeaders.AUTHORIZATION) String token,
                                @RequestBody String body) {
        if (isAuthenticated(token)) {
            //update config for ip or id
            if (ip != null || id != null) {
                CloudEventData<ConfigChangedEvent> configEvent = CloudEventBuilder.builder(ConfigChangedEvent.of(appName, body))
                        .withSource(RSocketAppContext.SOURCE)
                        .build();
                return Flux.fromIterable(serviceManager.getByAppName(appName)).filter(handler -> {
                    AppMetadata appMetadata = handler.getAppMetadata();
                    return appMetadata.getUuid().equals(id) || appMetadata.getIp().equals(ip);
                }).flatMap(responder -> responder.fireCloudEventToPeer(configEvent)).then(Mono.just("success"));
            } else {
                return confDiamond.put(appName + ":application.properties", body).map(aVoid -> "success");
            }
        } else {
            return Mono.error(new InvalidException("Failed to validate JWT token, please supply correct token."));
        }
    }

    @GetMapping("/last/{appName}")
    public Mono<String> fetch(@PathVariable(name = "appName") String appName, @RequestHeader(name = HttpHeaders.AUTHORIZATION) String token) {
        if (isAuthenticated(token)) {
            return confDiamond.get(appName + ":application.properties");
        } else {
            return Mono.error(new InvalidException("Failed to validate JWT token, please supply correct token."));
        }
    }

    /**
     * 校验认证
     */
    private boolean isAuthenticated(String token) {
        return !brokerConfig.isAuth() || Objects.nonNull(authenticationService.auth(token));
    }

}
