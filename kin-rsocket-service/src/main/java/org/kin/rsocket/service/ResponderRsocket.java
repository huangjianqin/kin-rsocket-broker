package org.kin.rsocket.service;

import io.rsocket.RSocket;
import org.kin.rsocket.core.event.CloudEventData;
import reactor.core.publisher.Mono;

/**
 * rsocket responder额外支持的接口方法
 *
 * @author huangjianqin
 * @date 2021/3/28
 */
public interface ResponderRsocket extends RSocket {
    /**
     * 给requester发送cloud event
     */
    Mono<Void> fireCloudEventToPeer(CloudEventData<?> cloudEvent);
}
