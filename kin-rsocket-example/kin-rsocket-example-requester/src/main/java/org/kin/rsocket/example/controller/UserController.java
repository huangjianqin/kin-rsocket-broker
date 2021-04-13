package org.kin.rsocket.example.controller;

import org.kin.rsocket.example.User;
import org.kin.rsocket.example.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author huangjianqin
 * @date 2021/4/9
 */
@RestController
@RequestMapping("/user")
public class UserController implements ApplicationRunner {
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

    @Override
    public void run(ApplicationArguments args) throws Exception {
//        while(true){
//            findAll().subscribe(System.out::println);
//            Thread.sleep(2_000);
////            find("A").subscribe(System.out::println);
//        }
    }
}
