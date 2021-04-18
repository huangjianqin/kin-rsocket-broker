package org.kin.rsocket.conf;

import org.kin.framework.collection.Tuple;
import org.kin.rsocket.core.RSocketService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 配置中心接口
 * key={app name}:{key name}
 *
 * @author huangjianqin
 * @date 2021/3/29
 */
@RSocketService(ConfDiamond.class)
public interface ConfDiamond {
    /** group和key分隔符 */
    String GROUP_KEY_SEPARATOR = ":";

    /** 获取已有的配置组 */
    Flux<String> getGroups();

    /** 寻找指定配置组下所有的配置keys */
    Flux<String> findKeysByGroup(String group);

    /** 寻找指定配置组下所有的配置, 以properties组成str */
    Mono<String> findKeyValuesByGroup(String group);

    /** 更新配置 */
    Mono<Void> put(String key, String value);

    /** 移除配置 */
    Mono<Void> remove(String key);

    /** 获取配置值 */
    Mono<String> get(String key);

    /**
     * 返回监控配置变化的Flux
     * 当key=group时, 即监控整组配置
     */
    Flux<Tuple<String, String>> watch(String key);
}
