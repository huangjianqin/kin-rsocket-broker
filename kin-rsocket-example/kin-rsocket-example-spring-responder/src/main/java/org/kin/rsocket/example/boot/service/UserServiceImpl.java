package org.kin.rsocket.example.boot.service;

import org.kin.rsocket.example.boot.User;
import org.kin.rsocket.example.boot.UserService;
import org.kin.rsocket.service.boot.support.RSocketHandler;
import org.kin.rsocket.service.boot.support.RSocketService;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author huangjianqin
 * @date 2021/8/23
 */
@Controller
@RSocketService("org.kin.spring.rsocket.example.UserService")
public class UserServiceImpl implements UserService {
    private static final List<User> USERS = Arrays.asList(
            User.of("A", 1),
            User.of("B", 2),
            User.of("C", 3),
            User.of("D", 4),
            User.of("E", 5)
    );

    @RSocketHandler("findAll")
    @Override
    public Flux<User> findAll() {
        return Flux.fromIterable(USERS);
    }

    @RSocketHandler("find")
    @Override
    public Mono<User> find(String name) {
        int random = ThreadLocalRandom.current().nextInt(100);
        if (random % 2 == 0) {
            return Mono.just(User.of(name, random));
        }
        return Mono.just(User.of("unknown", random));
    }
}
