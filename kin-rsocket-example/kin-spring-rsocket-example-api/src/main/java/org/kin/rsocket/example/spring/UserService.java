package org.kin.rsocket.example.spring;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author huangjianqin
 * @date 2021/4/9
 */
public interface UserService {
    Flux<User> findAll();

    Mono<User> find(String name);
}
