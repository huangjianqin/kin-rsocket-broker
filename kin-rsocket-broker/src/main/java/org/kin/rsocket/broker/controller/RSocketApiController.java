package org.kin.rsocket.broker.controller;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.rsocket.util.DefaultPayload;
import org.kin.rsocket.auth.AuthenticationService;
import org.kin.rsocket.auth.RSocketAppPrincipal;
import org.kin.rsocket.broker.RSocketBrokerProperties;
import org.kin.rsocket.broker.RSocketService;
import org.kin.rsocket.broker.RSocketServiceMeshInspector;
import org.kin.rsocket.broker.RSocketServiceRegistry;
import org.kin.rsocket.core.Endpoints;
import org.kin.rsocket.core.RSocketMimeType;
import org.kin.rsocket.core.metadata.GSVRoutingMetadata;
import org.kin.rsocket.core.metadata.MessageMimeTypeMetadata;
import org.kin.rsocket.core.metadata.RSocketCompositeMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Objects;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;

/**
 * 基于http协议请求rsocket服务调用
 *
 * @author huangjianqin
 * @date 2021/3/31
 */
@RestController
@RequestMapping("/api")
public class RSocketApiController {
    /** json编码元数据 */
    private static final MessageMimeTypeMetadata JSON_ENCODING_METADATA = MessageMimeTypeMetadata.from(RSocketMimeType.JSON);

    @Autowired
    private RSocketBrokerProperties rsocketBrokerProperties;
    @Autowired
    private RSocketServiceRegistry serviceRegistry;
    @Autowired
    private RSocketServiceMeshInspector serviceMeshInspector;
    @Autowired
    private AuthenticationService authenticationService;

    @RequestMapping(value = "/{service}/{method}", produces = {MediaType.APPLICATION_JSON_VALUE})
    public Mono<ResponseEntity<String>> handle(@PathVariable("service") String service,
                                               @PathVariable("method") String method,
                                               @RequestParam(name = "group", required = false, defaultValue = "") String group,
                                               @RequestParam(name = "version", required = false, defaultValue = "") String version,
                                               @RequestBody(required = false) byte[] body,
                                               @RequestHeader(name = "X-Endpoint", required = false, defaultValue = "") String endpoint,
                                               @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false, defaultValue = "") String token) {
        try {
            GSVRoutingMetadata routingMetadata = GSVRoutingMetadata.from(group, service, method, version);
            int serviceId = routingMetadata.serviceId();

            ByteBuf bodyBuf = body == null ? EMPTY_BUFFER : PooledByteBufAllocator.DEFAULT.buffer(body.length).writeBytes(body);
            RSocketService rsocketService;
            if (endpoint.startsWith(Endpoints.INSTANCE_ID)) {
                //存在endpoint
                int instanceId = Integer.parseInt(endpoint.substring(Endpoints.INSTANCE_ID.length()).trim());
                rsocketService = serviceRegistry.getByInstanceId(instanceId);
            } else {
                rsocketService = serviceRegistry.routeByServiceId(serviceId);
            }
            if (Objects.nonNull(rsocketService)) {
                if (rsocketBrokerProperties.isAuth()) {
                    RSocketAppPrincipal principal = authenticationService.auth(token);
                    if (principal == null || !serviceMeshInspector.isAllowed(principal, serviceId, rsocketService.getPrincipal())) {
                        return Mono.just(error(String.format("Service request not allowed '%s'", routingMetadata.gsv())));
                    }
                }
                RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.from(routingMetadata, JSON_ENCODING_METADATA);
                return rsocketService.requestResponse(DefaultPayload.create(bodyBuf, compositeMetadata.getContent()))
                        .map(payload -> {
                            HttpHeaders headers = new HttpHeaders();
                            headers.setContentType(MediaType.APPLICATION_JSON);
                            headers.setCacheControl(CacheControl.noCache().getHeaderValue());
                            return new ResponseEntity<>(payload.getDataUtf8(), headers, HttpStatus.OK);
                        });
            }
            return Mono.just(error(String.format("service not found, '%s'", routingMetadata.gsv())));
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
