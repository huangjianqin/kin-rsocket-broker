package org.kin.rsocket.example.consumer;

import org.kin.rsocket.example.User;
import org.kin.rsocket.example.UserService;
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
 * @date 2021/4/9
 */
@RestController
@RequestMapping("/user")
public class UserController {
    @Autowired(required = false)
//    //方法4, 直接注入field
//    @RSocketServiceReference(name = "org.kin.rsocket.example.UserService")
    private UserService userService;
    @Autowired(required = false)
    private org.kin.rsocket.example.consumer.UserService customUserService;
    /** 测试是否会复用对象 */
//    @RSocketServiceReference(name = "org.kin.rsocket.example.UserService")
    private UserService userServiceCopy;

    @GetMapping("/all")
    public Flux<User> findAll() {
        if (Objects.nonNull(userService)) {
            return userService.findAll();
        } else if (Objects.nonNull(customUserService)) {
            return customUserService.findAll();
        } else {
            return Flux.empty();
        }
    }

    @GetMapping("/{name}")
    public Mono<User> find(@PathVariable(name = "name") String name) {
        if (Objects.nonNull(userService)) {
            return userService.find(name);
        } else if (Objects.nonNull(customUserService)) {
            return customUserService.find(name);
        } else {
            return Mono.empty();
        }
    }
}
