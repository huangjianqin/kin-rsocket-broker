package org.kin.rsocket.example.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * @author huangjianqin
 * @date 2021/5/16
 */
@RestController
@RequestMapping("/config")
public class ConfigController {
    @Autowired
    private Config config;

    @GetMapping("/get")
    public Mono<String> get() {
        return Mono.just(config.toString());
    }
}
