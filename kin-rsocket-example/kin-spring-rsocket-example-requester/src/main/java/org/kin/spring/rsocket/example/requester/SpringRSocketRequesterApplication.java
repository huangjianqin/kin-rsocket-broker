package org.kin.spring.rsocket.example.requester;

import org.kin.spring.rsocket.support.EnableLoadBalanceSpringRSocketServiceReference;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * @author huangjianqin
 * @date 2021/8/23
 */
@SpringBootApplication
@EnableDiscoveryClient
//@EnableSpringRSocketServiceReference
@EnableLoadBalanceSpringRSocketServiceReference
public class SpringRSocketRequesterApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpringRSocketRequesterApplication.class, args);
    }
}
