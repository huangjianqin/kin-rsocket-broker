package org.kin.rsocket.example.springcloud;

import io.netty.buffer.ByteBuf;
import org.kin.rsocket.core.RSocketMimeType;
import org.kin.rsocket.core.ServiceMapping;
import org.kin.rsocket.example.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author huangjianqin
 * @date 2021/4/9
 */
@ReactiveFeignClient(value = "rsocket-example-requester")
@RequestMapping("/api/org.kin.rsocket.example.UserService")
public interface UserService {
    @GetMapping(value = "/findAll")
    Flux<User> findAll();

    @GetMapping(value = "/find")
    Mono<User> find(String name);

    /**
     * 测试参数为{@link ByteBuf}
     */
    @GetMapping(value = "/find1")
    @ServiceMapping(paramEncoding = RSocketMimeType.BINARY)
    Mono<User> find1(ByteBuf byteBuf);

    /**
     * 测试返回值为{@link ByteBuf}
     */
    @GetMapping(value = "/find2")
    Mono<ByteBuf> find2(String name);
}
