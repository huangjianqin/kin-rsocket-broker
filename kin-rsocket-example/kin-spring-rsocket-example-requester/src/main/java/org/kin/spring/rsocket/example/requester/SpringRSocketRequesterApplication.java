package org.kin.spring.rsocket.example.requester;

import org.kin.spring.rsocket.support.EnableSpringRSocketServiceReference;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author huangjianqin
 * @date 2021/8/23
 */
@SpringBootApplication
@EnableSpringRSocketServiceReference
public class SpringRSocketRequesterApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpringRSocketRequesterApplication.class, args);
    }
}
