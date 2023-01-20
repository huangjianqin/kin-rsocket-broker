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
                .jwtToken("eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJNb2NrIiwiYXVkIjoia2luIiwic2FzIjpbImRlZmF1bHQiXSwicm9sZXMiOlsiaW50ZXJuYWwiXSwiaXNzIjoiS2luUlNvY2tldEJyb2tlciIsImlkIjoiZDczZTE4ZWYtOTgwNS00ZmI3LWIyMTItOGZkNzhhOTU1YzE3Iiwib3JncyI6WyJkZWZhdWx0Il0sImlhdCI6MTY2MTgyNTQyNH0.Iv9WyGszd-QBWvGVtixUzG4mjF7i81tJr65CgGNDSxZrse5I174VnXlcpawKmcUSAlFAT4313MrjGecAxB0bucDXysP6DdLL6tq93kvBu1tCGvIKqz8FfaNBpPWtwljT5_KzwuBMjwxpOOkQFHrfITwNsrWCMrHtC-3wGZQsMXlu9Ysz8SBqwcPWH3GTox3jm8HoccGfLuIoWg6JQPAMZAi3eLC5kQtELmMqTfIqhd38Lj_yJmG25oAxtWLmZEzPT_6aCs6Md1bqz_7ZGALlJxn6z_dcLhx9XrOge7T57w223X5G0JtYhTReOvtzF1dBdeyvkkF9GLj5cKlhImk5DA")
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

            userService.find4("abc", "ABC".getBytes(StandardCharsets.UTF_8)).subscribe(System.out::println);

            Thread.sleep(1_000);
        } finally {
            brokerClient.dispose();
        }
    }
}
