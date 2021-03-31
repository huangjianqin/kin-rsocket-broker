package org.kin.rsocket.broker.config;

import org.kin.framework.collection.Tuple;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 配置中心接口
 *
 * @author huangjianqin
 * @date 2021/3/29
 */
public interface ConfDiamond {
    /** group和key分隔符 */
    String GROUP_KEY_SEPARATOR = ":";

    /** 获取已有的配置组 */
    Flux<String> getGroups();

    /** 寻找指定配置组下所有的配置keys */
    Flux<String> findKeysByGroup(String group);

    /** 更新配置 */
    Mono<Void> put(String key, String value);

    /** 移除配置 */
    Mono<Void> remove(String key);

    /** 获取配置值 */
    Mono<String> get(String key);

    /** 返回监控配置变化的Flux */
    Flux<Tuple<String, String>> watch(String key);
}
