package org.kin.rsocket.example.service;

import org.kin.rsocket.conf.server.EnableRSocketConfServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author huangjianqin
 * @date 2022/4/10
 */
@EnableRSocketConfServer
@SpringBootApplication
public class ConfServerServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfServerServiceApplication.class, args);
    }
}
