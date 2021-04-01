package org.kin.rsocket.spingcloud.gateway.http;

import io.netty.buffer.ByteBuf;
import io.rsocket.RSocket;
import io.rsocket.util.ByteBufPayload;
import org.kin.rsocket.auth.AuthenticationService;
import org.kin.rsocket.core.metadata.GSVRoutingMetadata;
import org.kin.rsocket.core.metadata.MessageMimeTypeMetadata;
import org.kin.rsocket.core.metadata.RSocketCompositeMetadata;
import org.kin.rsocket.core.metadata.RSocketMimeType;
import org.kin.rsocket.service.UpstreamClusterManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Objects;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;

/**
 * @author leijuan
 */
@Controller
public class HttpGatewayController {
    private static final MessageMimeTypeMetadata JSON_ENCODING_MIME_TYPE = MessageMimeTypeMetadata.of(RSocketMimeType.Json);

    @Autowired
    private AuthenticationService authenticationService;
    //todo 是否需要修改
    @Value("${restapi.auth-required}")
    private boolean authRequired;
    /** broker upstream cluster */
    private RSocket rsocket;

    public HttpGatewayController(UpstreamClusterManager upstreamClusterManager) {
        rsocket = upstreamClusterManager.getBroker();
    }

    @RequestMapping(value = "/api/{serviceName}/{method}", produces = {MediaType.APPLICATION_JSON_VALUE})
    public Mono<ResponseEntity<ByteBuf>> handle(@PathVariable("serviceName") String serviceName,
                                                @PathVariable("method") String method,
                                                @RequestParam(name = "group", required = false, defaultValue = "") String group,
                                                @RequestParam(name = "version", required = false, defaultValue = "") String version,
                                                @RequestBody(required = false) ByteBuf body,
                                                @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false, defaultValue = "") String token) {
        boolean authenticated;
        if (!authRequired) {
            authenticated = true;
        } else {
            authenticated = Objects.nonNull(authenticationService.auth(token));
        }
        if (!authenticated) {
            return Mono.error(new Exception("Failed to validate JWT token, please supply correct token."));
        }
        try {
            GSVRoutingMetadata routingMetadata = GSVRoutingMetadata.of(group, serviceName, method, version);
            RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(routingMetadata, JSON_ENCODING_MIME_TYPE);
            ByteBuf bodyBuf = body == null ? EMPTY_BUFFER : body;
            return rsocket.requestResponse(ByteBufPayload.create(bodyBuf, compositeMetadata.getContent()))
                    .map(payload -> {
                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        headers.setCacheControl(CacheControl.noCache().getHeaderValue());
                        return new ResponseEntity<>(payload.data(), headers, HttpStatus.OK);
                    });
        } catch (Exception e) {
            return Mono.error(e);
        }
    }
}
