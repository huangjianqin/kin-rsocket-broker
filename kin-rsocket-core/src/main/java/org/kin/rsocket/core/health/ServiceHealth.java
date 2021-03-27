package org.kin.rsocket.core.health;

import reactor.core.publisher.Mono;

/**
 * rsocket service health检查, 作为broker的一个常驻服务存在
 *
 * @author huangjianqin
 * @date 2021/3/26
 */
@FunctionalInterface
public interface ServiceHealth {
    int DOWN = -1;
    int UNKNOWN = 0;
    int SERVING = 1;

    /**
     * health status: 0:unknown, 1: serving, -1: out of service
     */
    Mono<Integer> check(String serviceName);
}
