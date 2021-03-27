package org.kin.rsocket.core;

import io.netty.buffer.ByteBuf;
import org.kin.framework.utils.ClassUtils;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author huangjianqin
 * @date 2021/3/26
 */
class ReactiveMethodSupport {
    /** bytes class */
    public static final List<Class<?>> BINARY_CLASS_LIST = Collections.unmodifiableList(Arrays.asList(ByteBuf.class, ByteBuffer.class, byte[].class));

    /** 对应方法 */
    protected Method method;
    /** 参数数量 */
    protected int paramCount;
    /** 方法返回类型 */
    protected Class<?> returnType;
    /** 方法返回类型泛型参数实际类型 */
    protected Class<?> inferredClassForReturn;

    protected ReactiveMethodSupport(Method method) {
        this.method = method;
        this.paramCount = method.getParameterCount();
        this.returnType = method.getReturnType();
        this.inferredClassForReturn = ClassUtils.getInferredClassForGeneric(method.getGenericReturnType());
    }

    //getter
    public Method getMethod() {
        return method;
    }

    public int getParamCount() {
        return paramCount;
    }

    public Class<?> getReturnType() {
        return returnType;
    }

    public Class<?> getInferredClassForReturn() {
        return inferredClassForReturn;
    }
}
