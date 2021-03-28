package org.kin.rsocket.service;

import io.rsocket.RSocket;
import org.kin.rsocket.core.event.CloudEventData;
import reactor.core.publisher.Mono;

/**
 * rsocket requester额外支持的接口方法
 *
 * @author huangjianqin
 * @date 2021/3/27
 */
public interface RequesterRsocket extends RSocket {
    /**
     * 向所有有效的upstream rsocket广播cloud event
     */
    Mono<Void> broadcastCloudEvent(CloudEventData<?> cloudEvent);

    /**
     * 强制刷新unhealth uri, 也就是强制重连
     */
    void refreshUnhealthyUris();
}
