package org.kin.rsocket.example;

import com.google.protobuf.StringValue;
import io.netty.buffer.ByteBuf;
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

    /**
     * 测试参数为{@link ByteBuf}
     */
    @ServiceMapping(paramEncoding = "application/octet-stream")
    Mono<User> find1(ByteBuf byteBuf);

    /**
     * 测试返回值为{@link ByteBuf}
     */
    Mono<ByteBuf> find2(String name);

    /**
     * 参数和返回值都是protobuf生成的
     */
    Mono<UserPb> findByPb(StringValue name);

    /**
     * 主动抛异常的方法
     * fire-forget
     */
    void exception1();

    /**
     * 主动抛异常的方法
     * request-response
     */
    Mono<User> exception2();

    @ServiceMapping(paramEncoding = "application/vnd.google.protobuf")
    Mono<Boolean> find3(String name, int age);
}
