package org.kin.rsocket.broker.controller;

import org.kin.framework.utils.CollectionUtils;
import org.kin.rsocket.broker.RSocketEndpoint;
import org.kin.rsocket.broker.RSocketServiceManager;
import org.kin.rsocket.core.domain.AppVO;
import org.kin.rsocket.core.metadata.AppMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * rsocket服务应用相关接口
 *
 * @author huangjianqin
 * @date 2021/3/30
 */
@RestController
@RequestMapping("/app")
public class AppController {
    @Autowired
    private RSocketServiceManager serviceManager;

    @GetMapping("/{appName}")
    public Flux<AppVO> queryByAppName(@PathVariable(name = "appName") String appName) {
        List<AppVO> apps = new ArrayList<>();
        Collection<RSocketEndpoint> rsocketEndpoints = serviceManager.getByAppName(appName);
        if (CollectionUtils.isEmpty(rsocketEndpoints)) {
            return Flux.empty();
        }
        for (RSocketEndpoint rsocketEndpoint : rsocketEndpoints) {
            AppMetadata appMetadata = rsocketEndpoint.getAppMetadata();
            apps.add(appMetadata.toVo());
        }
        return Flux.fromIterable(apps);
    }

    @RequestMapping("/all")
    public Mono<Map<String, Collection<String>>> all() {
        return Flux.fromIterable(serviceManager.getAllRSocketEndpoints())
                .map(RSocketEndpoint::getAppMetadata)
                .collectMultimap(AppMetadata::getName, AppMetadata::getIp);
    }
}
