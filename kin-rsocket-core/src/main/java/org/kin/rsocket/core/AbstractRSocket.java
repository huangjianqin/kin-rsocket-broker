package org.kin.rsocket.core;

import io.rsocket.RSocket;
import org.kin.rsocket.core.utils.RetryNonSerializedEmitFailureHandler;
import reactor.core.Scannable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import javax.annotation.Nonnull;

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
        onClose.emitEmpty(RetryNonSerializedEmitFailureHandler.RETRY_NON_SERIALIZED);
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public boolean isDisposed() {
        return onClose.scan(Scannable.Attr.TERMINATED);
    }

    @Nonnull
    @Override
    public Mono<Void> onClose() {
        return onClose.asMono();
    }

}
