package org.kin.rsocket.example;

import io.netty.buffer.ByteBuf;
import org.kin.rsocket.core.Required;
import org.kin.rsocket.core.ServiceMapping;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author huangjianqin
 * @date 2021/4/9
 */
public interface UserService {
    Flux<User> findAll();

    Mono<User> find(String name);

    @ServiceMapping(paramEncoding = "application/octet-stream")
    Mono<User> find1(ByteBuf byteBuf);

    Mono<ByteBuf> find2(String name);

    /**
     * 测试{@link org.kin.rsocket.core.Required}注解
     */
    Mono<Boolean> checkRequired(@Required int a,
                                @Required String s,
                                @Required String[] ss);
}
