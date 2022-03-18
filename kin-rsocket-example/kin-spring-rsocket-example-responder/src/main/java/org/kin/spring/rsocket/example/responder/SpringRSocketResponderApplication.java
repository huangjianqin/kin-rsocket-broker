package org.kin.spring.rsocket.example.responder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * @author huangjianqin
 * @date 2021/8/23
 */
@SpringBootApplication
@EnableDiscoveryClient
public class SpringRSocketResponderApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpringRSocketResponderApplication.class, args);
    }
}
