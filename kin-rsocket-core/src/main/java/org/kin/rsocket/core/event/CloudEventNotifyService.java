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
     */
    Mono<Void> notify(String appId, String cloudEventJson);

    /**
     * 向所有命名为XX的app广播事件
     */
    Mono<Void> notifyAll(String appName, String cloudEventJson);
}
