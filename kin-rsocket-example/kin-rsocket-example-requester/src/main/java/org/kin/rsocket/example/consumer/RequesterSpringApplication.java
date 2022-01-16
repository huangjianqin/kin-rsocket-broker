package org.kin.rsocket.example.consumer;

import org.kin.rsocket.springcloud.service.EnableRSocketServiceReference;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author huangjianqin
 * @date 2021/4/9
 */
@EnableRSocketServiceReference
@SpringBootApplication
public class RequesterSpringApplication {
    public static void main(String[] args) {
        SpringApplication.run(RequesterSpringApplication.class, args);
    }
}
