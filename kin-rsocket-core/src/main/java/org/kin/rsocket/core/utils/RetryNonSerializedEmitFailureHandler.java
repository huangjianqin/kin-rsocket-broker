package org.kin.rsocket.core.utils;

import reactor.core.publisher.SignalType;
import reactor.core.publisher.Sinks;

/**
 * @author huangjianqin
 * @date 2022/12/22
 */
public final class RetryNonSerializedEmitFailureHandler implements Sinks.EmitFailureHandler {

    public static final RetryNonSerializedEmitFailureHandler RETRY_NON_SERIALIZED =
            new RetryNonSerializedEmitFailureHandler();

    @Override
    public boolean onEmitFailure(SignalType signalType, Sinks.EmitResult emitResult) {
        //多线程emit可能会导致FAIL_NON_SERIALIZED, 故此重试emit, 如果subscriber写得不好, 可能会导致100% cpu, 一直retry
        return emitResult == Sinks.EmitResult.FAIL_NON_SERIALIZED;
    }
}
