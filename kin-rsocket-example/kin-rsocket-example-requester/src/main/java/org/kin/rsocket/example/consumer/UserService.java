package org.kin.rsocket.example.consumer;

import org.kin.rsocket.example.User;
import org.kin.rsocket.service.RSocketServiceReference;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 自定义的rsocket service, 接口方法签名必须remote rsocket service一直, 但允许部分方法缺失
 *
 * @author huangjianqin
 * @date 2021/5/20
 */
//方法3. 在接口配置@RSocketServiceReference
@RSocketServiceReference(name = "org.kin.rsocket.example.UserService")
public interface UserService {
    Flux<User> findAll();

    Mono<User> find(String name);
}
