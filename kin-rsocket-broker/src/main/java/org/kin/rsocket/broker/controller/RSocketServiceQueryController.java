package org.kin.rsocket.broker.controller;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.rsocket.Payload;
import io.rsocket.util.ByteBufPayload;
import org.kin.rsocket.broker.BrokerResponder;
import org.kin.rsocket.broker.RSocketServiceManager;
import org.kin.rsocket.core.RSocketMimeType;
import org.kin.rsocket.core.RSocketServiceInfoSupport;
import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.metadata.GSVRoutingMetadata;
import org.kin.rsocket.core.metadata.MessageMimeTypeMetadata;
import org.kin.rsocket.core.metadata.RSocketCompositeMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author huangjianqin
 * @date 2021/3/30
 */
@RestController
@RequestMapping("/services")
public class RSocketServiceQueryController {
    /** json编码元数据 */
    private static final MessageMimeTypeMetadata JSON_ENCODING_METADATA = MessageMimeTypeMetadata.of(RSocketMimeType.JSON);
    @Autowired
    private RSocketServiceManager serviceManager;

    @GetMapping("/{service}")
    public Flux<Map<String, Object>> query(@PathVariable(name = "service") String service) {
        return Flux.fromIterable(serviceManager.getAllServices())
                .filter(locator -> locator.getService().equals(service))
                .map(locator -> {
                    Map<String, Object> serviceInfoMap = new HashMap<>();
                    serviceInfoMap.put("count", serviceManager.countInstanceIds(locator.getId()));
                    if (locator.getGroup() != null) {
                        serviceInfoMap.put("group", locator.getGroup());
                    }
                    if (locator.getVersion() != null) {
                        serviceInfoMap.put("version", locator.getVersion());
                    }
                    return serviceInfoMap;
                });
    }

    @GetMapping(value = "/definition/{service}")
    public Mono<String> queryDefinition(@RequestParam(name = "group", defaultValue = "") String group,
                                        @PathVariable(name = "service") String service,
                                        @RequestParam(name = "version", defaultValue = "") String version) {
        ByteBuf bodyBuf = Unpooled.wrappedBuffer(("[\"".concat(service).concat("\"]")).getBytes(StandardCharsets.UTF_8));
        BrokerResponder brokerResponder = serviceManager.routeByServiceId(ServiceLocator.of(group, service, version).getId(), bodyBuf);
        if (Objects.nonNull(brokerResponder)) {
            GSVRoutingMetadata routingMetadata =
                    GSVRoutingMetadata.of("", RSocketServiceInfoSupport.class.getName() + ".getReactiveServiceInfoByName", "");
            RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(routingMetadata, JSON_ENCODING_METADATA);
            return brokerResponder
                    .requestResponse(ByteBufPayload.create(bodyBuf, compositeMetadata.getContent()))
                    .map(Payload::getDataUtf8);
        }
        return Mono.error(new Exception(String.format("Service not found '%s'", service)));
    }

}
