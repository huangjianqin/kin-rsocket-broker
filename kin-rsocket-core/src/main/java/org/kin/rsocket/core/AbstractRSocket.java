package org.kin.rsocket.core;

import io.rsocket.RSocket;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;

/**
 * RSocket通信抽象父类
 *
 * @author huangjianqin
 * @date 2021/3/23
 */
public class AbstractRSocket implements RSocket {
    /** close mono, 用于subscribe close信号然后进而额外的逻辑处理 */
    private final MonoProcessor<Void> onClose = MonoProcessor.create();

    @Override
    public void dispose() {
        onClose.onComplete();
    }

    @Override
    public boolean isDisposed() {
        return onClose.isDisposed();
    }

    @Override
    public Mono<Void> onClose() {
        return onClose;
    }

}
