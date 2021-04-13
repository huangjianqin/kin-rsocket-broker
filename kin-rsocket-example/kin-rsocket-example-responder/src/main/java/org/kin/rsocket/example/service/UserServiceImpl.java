package org.kin.rsocket.example.service;

import org.kin.rsocket.core.RSocketService;
import org.kin.rsocket.example.User;
import org.kin.rsocket.example.UserService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author huangjianqin
 * @date 2021/4/9
 */
@RSocketService(UserService.class)
@Service
public class UserServiceImpl implements UserService {
    private static final List<User> USERS = Arrays.asList(
            User.of("A", 1),
            User.of("B", 2),
            User.of("C", 3),
            User.of("D", 4),
            User.of("E", 5)
    );

    @Override
    public Flux<User> findAll() {
        return Flux.fromIterable(USERS);
    }

    @Override
    public Mono<User> find(String name) {
        int random = ThreadLocalRandom.current().nextInt(100);
        if (random % 2 == 0) {
            return Mono.just(User.of(name, random));
        }
        return Mono.empty();
    }
}
