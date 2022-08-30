package org.kin.rsocket.broker.controller;

import org.kin.rsocket.auth.AuthenticationService;
import org.kin.rsocket.auth.CredentialParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * @author huangjianqin
 * @date 2022/8/30
 */
@RestController
@RequestMapping("/auth")
public class AuthenticationController {
    @Autowired
    private AuthenticationService authenticationService;

    @GetMapping("/token")
    public Mono<String> token(@RequestBody CredentialParam param) {
        return Mono.just(authenticationService.generateCredentials(param));
    }
}
