package org.kin.rsocket.example.springcloud;

import org.kin.rsocket.example.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author huangjianqin
 * @date 2021/5/11
 */
@RestController
@RequestMapping("/user")
public class UserController {
    @Autowired
    private UserService userService;

    @GetMapping("/all")
    public Flux<User> findAll() {
        return userService.findAll();
    }

    @GetMapping("/{name}")
    public Mono<User> find(@PathVariable(name = "name") String name) {
        return userService.find(name);
    }
}
