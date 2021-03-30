package org.kin.rsocket.broker.controller;

import io.rsocket.exceptions.InvalidException;
import org.kin.rsocket.broker.RSocketBrokerProperties;
import org.kin.rsocket.broker.ServiceRouter;
import org.kin.rsocket.broker.config.ConfDiamond;
import org.kin.rsocket.broker.security.AuthenticationService;
import org.kin.rsocket.broker.security.JwtPrincipal;
import org.kin.rsocket.broker.security.RSocketAppPrincipal;
import org.kin.rsocket.core.RSocketAppContext;
import org.kin.rsocket.core.event.CloudEventBuilder;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.application.ConfigChangedEvent;
import org.kin.rsocket.core.metadata.AppMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.HashSet;
import java.util.UUID;

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
    private ServiceRouter serviceRouter;
    @Autowired
    private RSocketBrokerProperties brokerConfig;

    @PostMapping("/refresh/{appName}")
    public Mono<String> refresh(@PathVariable(name = "appName") String appName,
                                @RequestParam(name = "ip", required = false) String ip,
                                @RequestParam(name = "id", required = false) String id,
                                @RequestHeader(name = HttpHeaders.AUTHORIZATION) String jwtToken,
                                @RequestBody String body) {
        RSocketAppPrincipal appPrincipal = parseAppPrincipal(jwtToken);
        if (appPrincipal != null && appPrincipal.getSubject().equalsIgnoreCase("rsocket-admin")) {
            //update config for ip or id
            if (ip != null || id != null) {
                CloudEventData<ConfigChangedEvent> configEvent = CloudEventBuilder.builder(ConfigChangedEvent.of(appName, body))
                        .withSource(RSocketAppContext.SOURCE)
                        .build();
                return Flux.fromIterable(serviceRouter.getByAppName(appName)).filter(handler -> {
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
    public Mono<String> fetch(@PathVariable(name = "appName") String appName, @RequestHeader(name = HttpHeaders.AUTHORIZATION) String jwtToken) {
        RSocketAppPrincipal appPrincipal = parseAppPrincipal(jwtToken);
        if (appPrincipal != null &&
                (appName.equalsIgnoreCase(appPrincipal.getSubject()) ||
                        appPrincipal.getSubject().equalsIgnoreCase("rsocket-admin"))) {
            return confDiamond.get(appName + ":application.properties");
        } else {
            return Mono.error(new InvalidException("Failed to validate JWT token, please supply correct token."));
        }
    }

    /**
     * todo
     * 解析jwt认证
     */
    private RSocketAppPrincipal parseAppPrincipal(String jwtToken) {
        if (brokerConfig.isAuthRequired()) {
            return authenticationService.auth("Bearer", jwtToken.substring(jwtToken.indexOf(" ") + 1));
        } else {
            return new JwtPrincipal(UUID.randomUUID().toString(), "rsocket-admin",
                    Collections.singletonList("mock_owner"),
                    new HashSet<>(Collections.singletonList("admin")),
                    Collections.emptySet(),
                    new HashSet<>(Collections.singletonList("default")),
                    new HashSet<>(Collections.singletonList("1")));
        }
    }
}
