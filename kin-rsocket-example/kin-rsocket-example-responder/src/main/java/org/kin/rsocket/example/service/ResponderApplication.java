package org.kin.rsocket.example.service;

import org.kin.rsocket.example.UserService;
import org.kin.rsocket.service.RSocketBrokerClient;
import org.kin.rsocket.service.RSocketServiceProperties;

/**
 * 不使用spring容器启动rsocket service provider
 *
 * @author huangjianqin
 * @date 2021/4/17
 */
public class ResponderApplication {
    public static void main(String[] args) throws InterruptedException {
        RSocketServiceProperties properties = RSocketServiceProperties.builder()
                .brokers("tcp://127.0.0.1:10000")
                .jwtToken("eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJNb2NrIiwiYXVkIjoia2luIiwic2FzIjpbImRlZmF1bHQiXSwicm9sZXMiOlsiaW50ZXJuYWwiXSwiaXNzIjoiS2luUlNvY2tldEJyb2tlciIsImlkIjoiZDczZTE4ZWYtOTgwNS00ZmI3LWIyMTItOGZkNzhhOTU1YzE3Iiwib3JncyI6WyJkZWZhdWx0Il0sImlhdCI6MTY2MTgyNTQyNH0.Iv9WyGszd-QBWvGVtixUzG4mjF7i81tJr65CgGNDSxZrse5I174VnXlcpawKmcUSAlFAT4313MrjGecAxB0bucDXysP6DdLL6tq93kvBu1tCGvIKqz8FfaNBpPWtwljT5_KzwuBMjwxpOOkQFHrfITwNsrWCMrHtC-3wGZQsMXlu9Ysz8SBqwcPWH3GTox3jm8HoccGfLuIoWg6JQPAMZAi3eLC5kQtELmMqTfIqhd38Lj_yJmG25oAxtWLmZEzPT_6aCs6Md1bqz_7ZGALlJxn6z_dcLhx9XrOge7T57w223X5G0JtYhTReOvtzF1dBdeyvkkF9GLj5cKlhImk5DA")
                .loadBalance("weightedstats")
                .build();
        RSocketBrokerClient brokerClient = RSocketBrokerClient.builder("MockResponderApp", properties).buildAndInit();
        UserService userService = new UserServiceImpl();
        brokerClient.registerService(UserService.class, userService);
        brokerClient.serving();

        Thread.currentThread().join();
    }
}
