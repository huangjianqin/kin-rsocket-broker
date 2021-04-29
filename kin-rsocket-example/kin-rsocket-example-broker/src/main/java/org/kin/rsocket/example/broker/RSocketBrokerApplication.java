package org.kin.rsocket.example.broker;

import org.kin.rsocket.springcloud.broker.EnableRSocketBroker;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author huangjianqin
 * @date 2021/4/29
 */
@SpringBootApplication
@EnableRSocketBroker
public class RSocketBrokerApplication {
    public static void main(String[] args) {
        SpringApplication.run(RSocketBrokerApplication.class, args);
    }
}
