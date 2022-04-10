package org.kin.rsocket.core.conf;

import reactor.core.publisher.Mono;

/**
 * 供配置中心注册到broker的rsocket service接口
 *
 * @author huangjianqin
 * @date 2022/4/9
 */
public interface ConfigurationService {
    /**
     * 获取指定app的配置
     *
     * @param appName app name
     * @param key     配置key, 如果是启动配置, 则可以是application.properties
     * @return 配置内容
     */
    Mono<String> get(String appName, String key);
}
