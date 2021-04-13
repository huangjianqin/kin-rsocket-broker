package org.kin.rsocket.core;

import io.rsocket.RSocket;
import reactor.core.Scannable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * RSocket通信抽象父类
 *
 * @author huangjianqin
 * @date 2021/3/23
 */
public class AbstractRSocket implements RSocket {
    /** close mono, 用于subscribe close信号然后进而额外的逻辑处理 */
    private final Sinks.One<Void> onClose = Sinks.one();

    @Override
    public void dispose() {
        onClose.tryEmitEmpty();
    }

    @Override
    public boolean isDisposed() {
        //todo
        return onClose.scan(Scannable.Attr.TERMINATED);
    }

    @Override
    public Mono<Void> onClose() {
        return onClose.asMono();
    }

}
