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
 * rsocket broker权限相关接口
 *
 * @author huangjianqin
 * @date 2022/8/30
 */
@RestController
@RequestMapping("/auth")
public class AuthenticationController {
    @Autowired
    private AuthenticationService authenticationService;

    /**
     * 生成app jwt token接口
     */
    @GetMapping("/token")
    public Mono<String> token(@RequestBody CredentialParam param) {
        return Mono.just(authenticationService.generateCredentials(param));
    }
}
