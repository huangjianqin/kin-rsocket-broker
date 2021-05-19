package org.kin.rsocket.example.consumer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.kin.rsocket.example.UserService;
import org.kin.rsocket.service.RSocketServiceConnector;
import org.kin.rsocket.service.RSocketServiceProperties;
import org.kin.rsocket.service.RSocketServiceReferenceBuilder;

import java.nio.charset.StandardCharsets;

/**
 * @author huangjianqin
 * @date 2021/4/17
 */
public class RequesterApplication {
    public static void main(String[] args) throws InterruptedException {
        RSocketServiceProperties properties = RSocketServiceProperties.builder()
                .brokers("tcp://127.0.0.1:9999")
//                .endpoints(EndpointProperties.of(UserService.class.getCanonicalName(), "tcp://127.0.0.1:9101"))
                .jwtToken("eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJNb2NrIiwiYXVkIjoia2luIiwic2FzIjpbImRlZmF1bHQiXSwicm9sZXMiOlsiaW50ZXJuYWwiXSwiaXNzIjoiS2luUlNvY2tldEJyb2tlciIsImlkIjoiNmFkNTNiNDItMjEyNi00ZjE2LWEwNzQtY2I4MjBjZGZlYjFhIiwib3JncyI6WyJkZWZhdWx0Il0sImlhdCI6MTYxODI4MDkxOH0.e8O1ZSpoBKW2UJYXqnLM8d9zmLNDUa-AQsRu-cig0N9R2A-4-9TwN1mz4uuftigU6iX0EjxNCCghd6IldvcjK88af-MeMUkdEx4_83dBm0Ugjp70au0_BacF83MBfYBnDK_hZ3Ftu2_Plp83dLiHbU-h3TK4VT4xfDM5LbYFR_4zvTDK_42lnJqrP1HDFwcZcHLeHhhhZmzVhpLiUnkDRDGW4P7RBASOacI89IMw2zc15aLrRqs3qZRRxFwX0huHVI2fZFF_GC5tYh47RqNcDSWcc_vwo-PuTPTCkGvDM7QvpYzpdM95LsPC6Z95vfv0VRwSCewlCj5IINqnzvY-ZA")
                .port(9201)
                .build();
        RSocketServiceConnector connector = new RSocketServiceConnector("MockApp", properties);
        connector.connect();
        UserService userService = RSocketServiceReferenceBuilder.requester(UserService.class).upstreamClusterManager(connector).build();
        try {
            userService.findAll().subscribe(System.out::println);

            ByteBuf buffer = Unpooled.buffer();
            buffer.writeBytes("AA".getBytes(StandardCharsets.UTF_8));
            userService.find1(buffer).subscribe(System.out::println);

            userService.find2("AA").subscribe(byteBuf -> {
                byte[] bytes = new byte[byteBuf.readableBytes()];
                byteBuf.readBytes(bytes);

                System.out.println(new String(bytes, StandardCharsets.UTF_8));
            });

            userService.checkRequired(0, "", null).subscribe(System.out::println);
            userService.checkRequired(1, "", null).subscribe(System.out::println);
            userService.checkRequired(1, "aa", null).subscribe(System.out::println);
            userService.checkRequired(1, "aa", new String[]{"bb"}).subscribe(System.out::println);

            Thread.sleep(1_000);
        } finally {
            connector.close();
        }
    }
}
