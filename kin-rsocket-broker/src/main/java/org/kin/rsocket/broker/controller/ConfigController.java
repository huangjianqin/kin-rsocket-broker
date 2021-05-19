package org.kin.rsocket.broker.controller;

import io.rsocket.exceptions.InvalidException;
import org.kin.framework.utils.PropertiesUtils;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.auth.AuthenticationService;
import org.kin.rsocket.broker.RSocketBrokerProperties;
import org.kin.rsocket.broker.RSocketServiceManager;
import org.kin.rsocket.broker.cluster.BrokerManager;
import org.kin.rsocket.conf.ConfDiamond;
import org.kin.rsocket.core.event.CloudEventBuilder;
import org.kin.rsocket.core.event.ConfigChangedEvent;
import org.kin.rsocket.core.metadata.AppMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Objects;
import java.util.Properties;

/**
 * @author huangjianqin
 * @date 2021/3/30
 */
@RestController
@RequestMapping("/config")
public class ConfigController {
    private static final Logger log = LoggerFactory.getLogger(ConfigController.class);
    @Autowired
    private ConfDiamond confDiamond;
    @Autowired
    private AuthenticationService authenticationService;
    @Autowired
    private RSocketServiceManager serviceManager;
    @Autowired
    private RSocketBrokerProperties brokerConfig;
    @Autowired
    private BrokerManager brokerManager;

    /**
     * 刷新指定app配置
     */
    @PostMapping("/refresh/{appName}")
    public Mono<String> refresh(@PathVariable(name = "appName") String appName,
                                @RequestParam(name = "ip", required = false) String ip,
                                @RequestParam(name = "id", required = false) String id,
                                @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false, defaultValue = "") String token) {
        if (!isAuthenticated(token)) {
            return Mono.error(new InvalidException("Failed to validate JWT token, please supply correct token."));
        }

        boolean refreshAll = StringUtils.isBlank(ip) && StringUtils.isBlank(id);
        return confDiamond.findKeyValuesByGroup(appName)
                .map(content -> CloudEventBuilder.builder(ConfigChangedEvent.of(appName, content)).build())
                .flatMap(event -> Flux.fromIterable(serviceManager.getByAppName(appName))
                        .filter(handler -> {
                            AppMetadata appMetadata = handler.getAppMetadata();
                            return refreshAll || appMetadata.getUuid().equals(id) || appMetadata.getIp().equals(ip);
                        })
                        .flatMap(responder -> responder.fireCloudEvent(event))
                        .then(Mono.just("success")));
    }

    /**
     * 更新配置
     */
    @PostMapping("/update/{appName}")
    public Mono<String> update(@PathVariable(name = "appName") String appName,
                               @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false, defaultValue = "") String token,
                               //properties形式
                               @RequestBody String body) throws IOException {
        if (!isAuthenticated(token)) {
            return Mono.error(new InvalidException("Failed to validate JWT token, please supply correct token."));
        }

        Properties properties = PropertiesUtils.loadPropertiesContent(body);

        return Flux.fromIterable(properties.stringPropertyNames())
                .filter(key -> !key.contains(ConfDiamond.GROUP_KEY_SEPARATOR))
                //update conf
                .flatMap(key -> confDiamond.put(appName, key, properties.getProperty(key)))
                .collectList()
                //get latest conf
                .flatMap(l -> confDiamond.findKeyValuesByGroup(appName))
                //build ConfigChangedEvent
                .map(content -> CloudEventBuilder.builder(ConfigChangedEvent.of(appName, content)).build())
                //broadcast event to all broker
                .flatMap(event -> brokerManager.broadcast(event).then(Mono.just("success")));
    }

    @GetMapping("/last/{appName}")
    public Mono<String> fetch(@PathVariable(name = "appName") String appName,
                              @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false, defaultValue = "") String token) {
        if (!isAuthenticated(token)) {
            return Mono.error(new InvalidException("Failed to validate JWT token, please supply correct token."));
        }

        return confDiamond.findKeyValuesByGroup(appName);
    }

    /**
     * 校验认证
     */
    private boolean isAuthenticated(String token) {
        return !brokerConfig.isAuth() || Objects.nonNull(authenticationService.auth(token));
    }

}
