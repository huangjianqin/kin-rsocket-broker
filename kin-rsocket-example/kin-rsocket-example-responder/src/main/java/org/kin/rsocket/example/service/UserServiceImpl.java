package org.kin.rsocket.example.service;

import com.google.protobuf.StringValue;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.kin.rsocket.core.RSocketService;
import org.kin.rsocket.core.utils.JSON;
import org.kin.rsocket.example.User;
import org.kin.rsocket.example.UserPb;
import org.kin.rsocket.example.UserService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author huangjianqin
 * @date 2021/4/9
 */
@RSocketService(UserService.class)
@Service
public class UserServiceImpl implements UserService {
    private static final List<User> USERS = Arrays.asList(
            User.of("A", 1),
            User.of("B", 2),
            User.of("C", 3),
            User.of("D", 4),
            User.of("E", 5)
    );

    @Override
    public Flux<User> findAll() {
        return Flux.fromIterable(USERS);
    }

    @Override
    public Mono<User> find(String name) {
        int random = ThreadLocalRandom.current().nextInt(100);
        if (random % 2 == 0) {
            return Mono.just(User.of(name, random));
        }
        return Mono.just(User.of("unknown", random));
    }

    @Override
    public Mono<User> find1(ByteBuf byteBuf) {
        byte[] bytes = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(bytes);

        String name = new String(bytes, StandardCharsets.UTF_8);
        return find(name);
    }

    @Override
    public Mono<ByteBuf> find2(String name) {
        return find(name).map(u -> {
            String userJson = JSON.write(u);
            ByteBuf buffer = Unpooled.buffer();
            buffer.writeBytes(userJson.getBytes(StandardCharsets.UTF_8));
            return buffer;
        });
    }

    @Override
    public Mono<Boolean> checkRequired(int a, String s, String[] ss) {
        return Mono.just(true);
    }

    @Override
    public Mono<UserPb> findByPb(StringValue name) {
        String nameValue = name.getValue();
        int random = ThreadLocalRandom.current().nextInt(100);
        if (random % 2 == 0) {
            return Mono.just(UserPb.newBuilder().setName(nameValue).setAge(random).build());
        }
        return Mono.just(UserPb.newBuilder().setName("unknown").setAge(random).build());
    }
}
