package org.kin.rsocket.spingcloud.broker.gateway.http;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * RSocket Broker HTTP Gateway App
 *
 * @author huangjianqin
 * @date 2021/4/20
 */
@SpringBootApplication
public class RSocketBrokerHttpGatewayApp {
    public static void main(String[] args) {
        SpringApplication.run(RSocketBrokerHttpGatewayApp.class, args);
    }
}
