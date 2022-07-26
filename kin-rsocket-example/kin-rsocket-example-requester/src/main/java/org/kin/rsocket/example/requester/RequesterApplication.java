package org.kin.rsocket.example.requester;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.kin.rsocket.core.upstream.loadbalance.UpstreamLoadBalanceStrategy;
import org.kin.rsocket.example.UserService;
import org.kin.rsocket.service.RSocketBrokerClient;
import org.kin.rsocket.service.RSocketServiceProperties;
import org.kin.rsocket.service.RSocketServiceReferenceBuilder;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 不使用spring容器启动rsocket service consumer
 *
 * @author huangjianqin
 * @date 2021/4/17
 */
public class RequesterApplication {
    public static void main(String[] args) throws InterruptedException {
        RSocketServiceProperties properties = RSocketServiceProperties.builder()
                .brokers("tcp://127.0.0.1:9999")
//                .endpoints(EndpointProperties.of(UserService.class.getName(), "tcp://127.0.0.1:9101"))
                .jwtToken("eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJNb2NrIiwiYXVkIjoia2luIiwic2FzIjpbImRlZmF1bHQiXSwicm9sZXMiOlsiaW50ZXJuYWwiXSwiaXNzIjoiS2luUlNvY2tldEJyb2tlciIsImlkIjoiNmFkNTNiNDItMjEyNi00ZjE2LWEwNzQtY2I4MjBjZGZlYjFhIiwib3JncyI6WyJkZWZhdWx0Il0sImlhdCI6MTYxODI4MDkxOH0.e8O1ZSpoBKW2UJYXqnLM8d9zmLNDUa-AQsRu-cig0N9R2A-4-9TwN1mz4uuftigU6iX0EjxNCCghd6IldvcjK88af-MeMUkdEx4_83dBm0Ugjp70au0_BacF83MBfYBnDK_hZ3Ftu2_Plp83dLiHbU-h3TK4VT4xfDM5LbYFR_4zvTDK_42lnJqrP1HDFwcZcHLeHhhhZmzVhpLiUnkDRDGW4P7RBASOacI89IMw2zc15aLrRqs3qZRRxFwX0huHVI2fZFF_GC5tYh47RqNcDSWcc_vwo-PuTPTCkGvDM7QvpYzpdM95LsPC6Z95vfv0VRwSCewlCj5IINqnzvY-ZA")
                .loadBalance(UpstreamLoadBalanceStrategy.WEIGHTED_STATS)
                .build();
        RSocketBrokerClient brokerClient = RSocketBrokerClient.builder("MockRequesterApp", properties).buildAndInit();
        UserService userService = RSocketServiceReferenceBuilder.reference(UserService.class).upstreamClusters(brokerClient).build();
        try {
            userService.findAll().subscribe(r -> System.out.println("findAll result: " + r));

            ByteBuf buffer = Unpooled.buffer();
            buffer.writeBytes("AA".getBytes(StandardCharsets.UTF_8));
            userService.find1(buffer).subscribe(r -> System.out.println("find1 result: " + r));

            userService.find2("AA").subscribe(byteBuf -> {
                byte[] bytes = new byte[byteBuf.readableBytes()];
                byteBuf.readBytes(bytes);

                System.out.println("find2 result: " + new String(bytes, StandardCharsets.UTF_8));
            });

            userService.exception1();

            userService.exception2().subscribe();

            userService.find3("BB", ThreadLocalRandom.current().nextInt(100)).subscribe(r -> System.out.println("find3 result: " + r));

            Thread.sleep(1_000);
        } finally {
            brokerClient.close();
        }
    }
}
