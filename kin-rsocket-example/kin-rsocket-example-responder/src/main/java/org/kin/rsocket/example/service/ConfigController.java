package org.kin.rsocket.example.service;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;

/**
 * @author huangjianqin
 * @date 2021/5/16
 */
@RestController
@RequestMapping("/config")
public class ConfigController {
    @Resource
    private Config config;

    @GetMapping("/get")
    public Mono<String> get() {
        return Mono.just(config.toString());
    }
}
