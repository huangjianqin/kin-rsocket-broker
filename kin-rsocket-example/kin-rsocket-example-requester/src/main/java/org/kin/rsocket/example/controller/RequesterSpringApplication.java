package org.kin.rsocket.example.controller;

import org.kin.rsocket.springcloud.service.EnableRSocketService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author huangjianqin
 * @date 2021/4/9
 */
@EnableRSocketService
@SpringBootApplication
public class RequesterSpringApplication {
    public static void main(String[] args) throws InterruptedException {
        SpringApplication.run(RequesterSpringApplication.class, args);
        //todo
        while (true) {
            Thread.sleep(2_000);
        }
    }
}
