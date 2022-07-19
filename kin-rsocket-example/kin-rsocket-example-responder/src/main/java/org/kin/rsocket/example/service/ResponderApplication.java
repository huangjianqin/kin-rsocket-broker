package org.kin.rsocket.example.service;

import org.kin.rsocket.example.UserService;
import org.kin.rsocket.service.RSocketServiceProperties;
import org.kin.rsocket.service.RSocketServiceRequester;

/**
 * 不使用spring容器启动rsocket service provider
 *
 * @author huangjianqin
 * @date 2021/4/17
 */
public class ResponderApplication {
    public static void main(String[] args) throws InterruptedException {
        RSocketServiceProperties properties = RSocketServiceProperties.builder()
                .brokers("tcp://127.0.0.1:9999")
                .jwtToken("eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJNb2NrIiwiYXVkIjoia2luIiwic2FzIjpbImRlZmF1bHQiXSwicm9sZXMiOlsiaW50ZXJuYWwiXSwiaXNzIjoiS2luUlNvY2tldEJyb2tlciIsImlkIjoiNmFkNTNiNDItMjEyNi00ZjE2LWEwNzQtY2I4MjBjZGZlYjFhIiwib3JncyI6WyJkZWZhdWx0Il0sImlhdCI6MTYxODI4MDkxOH0.e8O1ZSpoBKW2UJYXqnLM8d9zmLNDUa-AQsRu-cig0N9R2A-4-9TwN1mz4uuftigU6iX0EjxNCCghd6IldvcjK88af-MeMUkdEx4_83dBm0Ugjp70au0_BacF83MBfYBnDK_hZ3Ftu2_Plp83dLiHbU-h3TK4VT4xfDM5LbYFR_4zvTDK_42lnJqrP1HDFwcZcHLeHhhhZmzVhpLiUnkDRDGW4P7RBASOacI89IMw2zc15aLrRqs3qZRRxFwX0huHVI2fZFF_GC5tYh47RqNcDSWcc_vwo-PuTPTCkGvDM7QvpYzpdM95LsPC6Z95vfv0VRwSCewlCj5IINqnzvY-ZA")
                .loadBalance("weightedstats")
                .build();
        RSocketServiceRequester requester = RSocketServiceRequester.builder("MockResponderApp", properties).buildAndInit();
        UserService userService = new UserServiceImpl();
        requester.registerService(UserService.class, userService);
        requester.serving();

        Thread.currentThread().join();
    }
}
