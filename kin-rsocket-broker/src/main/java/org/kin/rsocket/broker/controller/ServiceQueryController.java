package org.kin.rsocket.broker.controller;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.rsocket.Payload;
import io.rsocket.util.ByteBufPayload;
import org.kin.rsocket.broker.ServiceManager;
import org.kin.rsocket.broker.ServiceResponder;
import org.kin.rsocket.core.RSocketMimeType;
import org.kin.rsocket.core.ReactiveServiceRegistry;
import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.metadata.GSVRoutingMetadata;
import org.kin.rsocket.core.metadata.MessageMimeTypeMetadata;
import org.kin.rsocket.core.metadata.RSocketCompositeMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
public class ServiceQueryController {
    /** json编码元数据 */
    private static final MessageMimeTypeMetadata JSON_ENCODING_METADATA = MessageMimeTypeMetadata.of(RSocketMimeType.Json);
    @Autowired
    private ServiceManager serviceManager;

    @GetMapping("/{serviceName}")
    public Flux<Map<String, Object>> query(@PathVariable(name = "serviceName") String serviceName) {
        return Flux.fromIterable(serviceManager.getAllServices())
                .filter(locator -> locator.getService().equals(serviceName))
                .map(locator -> {
                    Map<String, Object> serviceInfo = new HashMap<>();
                    serviceInfo.put("count", serviceManager.countInstanceIds(locator.getId()));
                    if (locator.getGroup() != null) {
                        serviceInfo.put("group", locator.getGroup());
                    }
                    if (locator.getVersion() != null) {
                        serviceInfo.put("version", locator.getVersion());
                    }
                    return serviceInfo;
                });
    }

    @GetMapping(value = "/definition/{group}/{serviceName}/{version}")
    public Mono<String> queryDefinition(@PathVariable(name = "group") String group,
                                        @PathVariable(name = "serviceName") String serviceName,
                                        @PathVariable(name = "version") String version) {
        //todo 不带group和版本号可以???
        ServiceResponder brokerResponder = serviceManager.getByServiceId(ServiceLocator.of(group, serviceName, version).getId());
        if (Objects.nonNull(brokerResponder)) {
            GSVRoutingMetadata routingMetadata =
                    GSVRoutingMetadata.of("", ReactiveServiceRegistry.class.getCanonicalName() + ".getReactiveServiceInfoByName", "");
            RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(routingMetadata, JSON_ENCODING_METADATA);
            ByteBuf bodyBuf = Unpooled.wrappedBuffer(("[\"" + serviceName + "\"]").getBytes(StandardCharsets.UTF_8));
            return brokerResponder.getPeerRsocket()
                    .requestResponse(ByteBufPayload.create(bodyBuf, compositeMetadata.getContent()))
                    .map(Payload::getDataUtf8);
        }
        return Mono.error(new Exception(String.format("Service not found '%s'", serviceName)));
    }

}
