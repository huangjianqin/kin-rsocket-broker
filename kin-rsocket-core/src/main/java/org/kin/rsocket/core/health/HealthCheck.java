package org.kin.rsocket.core.health;

import reactor.core.publisher.Mono;

/**
 * rsocket service health检查, 作为broker的一个常驻服务存在
 *
 * @author huangjianqin
 * @date 2021/3/26
 */
@FunctionalInterface
public interface HealthCheck {
    /** 下线 */
    int DOWN = -1;
    /** 未知 */
    int UNKNOWN = 0;
    /** 正在服务中 */
    int SERVING = 1;

    /**
     * health status: 0:unknown, 1: serving, -1: out of service
     */
    Mono<Integer> check(String service);
}
