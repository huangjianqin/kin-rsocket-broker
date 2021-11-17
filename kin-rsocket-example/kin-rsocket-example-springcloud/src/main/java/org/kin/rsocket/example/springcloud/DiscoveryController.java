package org.kin.rsocket.example.springcloud;

import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import javax.annotation.Resource;

/**
 * @author huangjianqin
 * @date 2021/5/11
 */
@RestController
@RequestMapping("/discovery")
public class DiscoveryController {
    @Resource
    private ReactiveDiscoveryClient reactiveDiscoveryClient;

    @GetMapping("/all")
    public Flux<String> services() {
        return reactiveDiscoveryClient.getServices();
    }
}
