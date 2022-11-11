package org.kin.rsocket.example.cloud;

import org.kin.rsocket.service.boot.EnableRSocketService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import reactivefeign.spring.config.EnableReactiveFeignClients;

/**
 * @author huangjianqin
 * @date 2021/5/11
 */
@EnableRSocketService
@EnableReactiveFeignClients
@SpringBootApplication
@EnableDiscoveryClient
public class RequesterCloudApplication {
    public static void main(String[] args) {
        SpringApplication.run(RequesterCloudApplication.class, args);
    }
}
