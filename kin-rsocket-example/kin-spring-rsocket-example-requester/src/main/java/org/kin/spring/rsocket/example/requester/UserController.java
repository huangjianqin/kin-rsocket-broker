package org.kin.spring.rsocket.example.requester;

import org.kin.spring.rsocket.example.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * @author huangjianqin
 * @date 2021/8/23
 */
@RestController
@RequestMapping("/user")
public class UserController {
    @Autowired
    private UserService userService;

    @GetMapping("/all")
    public Flux<User> findAll() {
        if (Objects.nonNull(userService)) {
            return userService.findAll();
        } else {
            return Flux.empty();
        }
    }

    @GetMapping("/{name}")
    public Mono<User> find(@PathVariable(name = "name") String name) {
        if (Objects.nonNull(userService)) {
            return userService.find(name);
        } else {
            return Mono.empty();
        }
    }
}
