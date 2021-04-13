package org.kin.rsocket.example.controller;

import org.kin.rsocket.service.EnableRSocketService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author huangjianqin
 * @date 2021/4/9
 */
@EnableRSocketService
@SpringBootApplication
public class RequesterApplication {
    public static void main(String[] args) throws InterruptedException {
        SpringApplication.run(RequesterApplication.class, args);
        //todo
        while (true) {
            Thread.sleep(2_000);
        }
    }
}
