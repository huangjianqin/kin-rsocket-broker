package org.kin.rsocket.core;

import io.rsocket.RSocket;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * @author huangjianqin
 * @date 2021/3/27
 */
public class RoundRobinSelector implements Selector {
    /** 计数器 */
    private int counter;

    @Override
    public Mono<RSocket> apply(List<RSocket> rSockets) {
        return Mono.just(rSockets.get(counter++ % rSockets.size()));
    }
}
