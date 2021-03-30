package org.kin.rsocket.broker.controller;

import org.kin.framework.utils.CollectionUtils;
import org.kin.rsocket.broker.Responder;
import org.kin.rsocket.broker.ServiceRouter;
import org.kin.rsocket.core.metadata.AppMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.*;

/**
 * @author huangjianqin
 * @date 2021/3/30
 */
@RestController
@RequestMapping("/app")
public class AppQueryController {
    @Autowired
    private ServiceRouter serviceRouter;

    @GetMapping("/{appName}")
    public Flux<Map<String, Object>> query(@PathVariable(name = "appName") String appName) {
        List<Map<String, Object>> apps = new ArrayList<>();
        Collection<Responder> responders = serviceRouter.getByAppName(appName);
        if (CollectionUtils.isNonEmpty(responders)) {
            for (Responder handler : responders) {
                Map<String, Object> app = new HashMap<>();
                AppMetadata appMetadata = handler.getAppMetadata();
                app.put("ip", appMetadata.getIp());
                app.put("uuid", appMetadata.getUuid());
                app.put("startedAt", appMetadata.getConnectedAt());
                apps.add(app);
            }
        }
        return Flux.fromIterable(apps);
    }
}
