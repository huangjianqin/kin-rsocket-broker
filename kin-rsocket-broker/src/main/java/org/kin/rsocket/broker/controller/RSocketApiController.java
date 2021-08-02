package org.kin.rsocket.broker.controller;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.rsocket.util.DefaultPayload;
import org.kin.rsocket.auth.AuthenticationService;
import org.kin.rsocket.auth.RSocketAppPrincipal;
import org.kin.rsocket.broker.BrokerResponder;
import org.kin.rsocket.broker.RSocketServiceManager;
import org.kin.rsocket.broker.RSocketServiceMeshInspector;
import org.kin.rsocket.core.RSocketMimeType;
import org.kin.rsocket.core.metadata.GSVRoutingMetadata;
import org.kin.rsocket.core.metadata.MessageMimeTypeMetadata;
import org.kin.rsocket.core.metadata.RSocketCompositeMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Objects;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;

/**
 * 以http形式请求服务调用, 不走rsocket
 *
 * @author huangjianqin
 * @date 2021/3/31
 */
@RestController
@RequestMapping("/api")
public class RSocketApiController {
    /** json编码元数据 */
    private static final MessageMimeTypeMetadata JSON_ENCODING_METADATA = MessageMimeTypeMetadata.of(RSocketMimeType.JSON);

    @Value("${kin.rsocket.broker.auth}")
    private boolean authRequired;
    @Autowired
    private RSocketServiceManager serviceManager;
    @Autowired
    private RSocketServiceMeshInspector serviceMeshInspector;
    @Autowired
    private AuthenticationService authenticationService;

    @RequestMapping(value = "/{serviceName}/{method}", produces = {MediaType.APPLICATION_JSON_VALUE})
    public Mono<ResponseEntity<String>> handle(@PathVariable("serviceName") String serviceName,
                                               @PathVariable("method") String method,
                                               @RequestParam(name = "group", required = false, defaultValue = "") String group,
                                               @RequestParam(name = "version", required = false, defaultValue = "") String version,
                                               @RequestBody(required = false) byte[] body,
                                               @RequestHeader(name = "X-Endpoint", required = false, defaultValue = "") String endpoint,
                                               @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false, defaultValue = "") String token) {
        try {
            GSVRoutingMetadata routingMetadata = GSVRoutingMetadata.of(group, serviceName, method, version);
            int serviceId = routingMetadata.serviceId();

            BrokerResponder responder;
            if (!endpoint.isEmpty() && endpoint.startsWith("id:")) {
                //存在endpoint
                int instanceId = Integer.parseInt(endpoint.substring(3).trim());
                responder = serviceManager.getByInstanceId(instanceId);
            } else {
                responder = serviceManager.getByServiceId(serviceId);
            }
            if (Objects.nonNull(responder)) {
                if (authRequired) {
                    RSocketAppPrincipal principal = authenticationService.auth(token);
                    if (principal == null || !serviceMeshInspector.isAllowed(principal, serviceId, responder.getPrincipal())) {
                        return Mono.just(error(String.format("Service request not allowed '%s'", routingMetadata.gsv())));
                    }
                }
                RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(routingMetadata, JSON_ENCODING_METADATA);
                ByteBuf bodyBuf = body == null ? EMPTY_BUFFER : Unpooled.wrappedBuffer(body);
                return responder.requestResponse(DefaultPayload.create(bodyBuf, compositeMetadata.getContent()))
                        .map(payload -> {
                            HttpHeaders headers = new HttpHeaders();
                            headers.setContentType(MediaType.APPLICATION_JSON);
                            headers.setCacheControl(CacheControl.noCache().getHeaderValue());
                            return new ResponseEntity<>(payload.getDataUtf8(), headers, HttpStatus.OK);
                        });
            }
            return Mono.just(error(String.format("Service not found '%s'", routingMetadata.gsv())));
        } catch (Exception e) {
            return Mono.just(error(e.getMessage()));
        }
    }

    /**
     * 请求异常统一处理
     */
    private ResponseEntity<String> error(String errorText) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setCacheControl(CacheControl.noCache().getHeaderValue());
        return new ResponseEntity<>(errorText, headers, HttpStatus.BAD_REQUEST);
    }

}
