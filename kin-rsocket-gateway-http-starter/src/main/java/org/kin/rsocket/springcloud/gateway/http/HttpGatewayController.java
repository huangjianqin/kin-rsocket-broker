package org.kin.rsocket.springcloud.gateway.http;

import io.netty.buffer.ByteBuf;
import io.rsocket.RSocket;
import io.rsocket.util.ByteBufPayload;
import org.kin.rsocket.auth.AuthenticationService;
import org.kin.rsocket.core.RSocketMimeType;
import org.kin.rsocket.core.metadata.GSVRoutingMetadata;
import org.kin.rsocket.core.metadata.MessageMimeTypeMetadata;
import org.kin.rsocket.core.metadata.RSocketCompositeMetadata;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Objects;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;

/**
 * @author huangjianqin
 * @date 2021/4/20
 */
@RestController
@RequestMapping("/api")
public class HttpGatewayController {
    private static final MessageMimeTypeMetadata JSON_ENCODING_MIME_TYPE = MessageMimeTypeMetadata.of(RSocketMimeType.Json);

    private final AuthenticationService authenticationService;
    private final RSocketBrokerHttpGatewayProperties config;
    /** broker upstream cluster */
    private final RSocket brokerRSocket;

    public HttpGatewayController(RSocket brokerRSocket,
                                 AuthenticationService authenticationService,
                                 RSocketBrokerHttpGatewayProperties config) {
        this.brokerRSocket = brokerRSocket;
        this.authenticationService = authenticationService;
        this.config = config;
    }

    @RequestMapping(value = "/{serviceName}/{method}", produces = {MediaType.APPLICATION_JSON_VALUE})
    public Mono<ResponseEntity<ByteBuf>> handle(@PathVariable("serviceName") String serviceName,
                                                @PathVariable("method") String method,
                                                @RequestParam(name = "group", required = false, defaultValue = "") String group,
                                                @RequestParam(name = "version", required = false, defaultValue = "") String version,
                                                @RequestBody(required = false) ByteBuf body,
                                                @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false, defaultValue = "") String token) {
        boolean authenticated;
        if (!config.isRestapiAuth()) {
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
            return brokerRSocket.requestResponse(ByteBufPayload.create(bodyBuf, compositeMetadata.getContent()))
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
