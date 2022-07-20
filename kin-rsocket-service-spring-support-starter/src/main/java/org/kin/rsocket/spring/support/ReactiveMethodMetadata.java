package org.kin.rsocket.spring.support;

import io.rsocket.frame.FrameType;
import org.kin.framework.utils.ClassUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;

/**
 * @author huangjianqin
 * @date 2021/8/22
 */
final class ReactiveMethodMetadata {
    /** rsocket frame type */
    private final FrameType frameType;
    /** 方法返回类型泛型参数实际类型 */
    private final Class<?> inferredClassForReturn;

    public ReactiveMethodMetadata(Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        FrameType frameType = null;
        if (parameterTypes.length > 0) {
            if (Flux.class.isAssignableFrom(method.getParameterTypes()[0])) {
                frameType = FrameType.REQUEST_CHANNEL;
            }
        }
        //参数不含Flux
        if (frameType == null) {
            if (Mono.class.isAssignableFrom(method.getReturnType())) {
                frameType = FrameType.REQUEST_RESPONSE;
            } else if (Flux.class.isAssignableFrom(method.getReturnType())) {
                frameType = FrameType.REQUEST_STREAM;
            } else {
                frameType = FrameType.REQUEST_FNF;
            }
        }

        this.frameType = frameType;
        inferredClassForReturn = ClassUtils.getInferredClassForGeneric(method.getGenericReturnType());
    }

    //getter
    public FrameType getFrameType() {
        return frameType;
    }

    public Class<?> getInferredClassForReturn() {
        return inferredClassForReturn;
    }
}
