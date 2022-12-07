package org.kin.rsocket.core.event;

import reactor.core.publisher.Mono;

/**
 * broker/service端广播cloud event 服务
 * 部署在broker上
 *
 * @author huangjianqin
 * @date 2021/8/11
 */
public interface CloudEventNotifyService {
    /**
     * 向指定app广播事件
     *
     * @param appId           app id
     * @param cloudEventBytes cloud event bytes
     * @return complete signal
     */
    Mono<Void> notify(String appId, byte[] cloudEventBytes);

    /**
     * 向所有命名为XX的app广播事件
     *
     * @param appName         app name
     * @param cloudEventBytes cloud event bytes
     * @return complete signal
     */
    Mono<Void> notifyAll(String appName, byte[] cloudEventBytes);
}
