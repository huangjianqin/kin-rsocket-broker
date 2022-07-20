package org.kin.rsocket.example.cloud.function;

import org.kin.rsocket.cloud.function.RSocketFunction;
import org.kin.rsocket.example.User;
import org.kin.rsocket.service.spring.EnableRSocketService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author huangjianqin
 * @date 2022/7/19
 */
@SpringBootApplication
@EnableRSocketService
public class RSocketFunctionApplication {
    private static final List<User> USERS = Arrays.asList(
            User.of("A", 1),
            User.of("B", 2),
            User.of("C", 3),
            User.of("D", 4),
            User.of("E", 5)
    );

    public static void main(String[] args) {
        SpringApplication.run(RSocketFunctionApplication.class, args);
    }

    @RSocketFunction()
    @Bean("org.kin.rsocket.example.UserService.findAll")
    public Supplier<Flux<User>> org$Kin$RSocket$Example$UserService$findAll() {
        return () -> Flux.fromIterable(USERS);
    }

    @Bean("org.kin.rsocket.example.UserService.find")
    public Function<String, Mono<User>> org$Kin$RSocket$Example$UserService$find() {
        return name -> {
            int random = ThreadLocalRandom.current().nextInt(100);
            if (random % 2 == 0) {
                return Mono.just(User.of(name, random));
            }
            return Mono.just(User.of("unknown", random));
        };
    }
}
