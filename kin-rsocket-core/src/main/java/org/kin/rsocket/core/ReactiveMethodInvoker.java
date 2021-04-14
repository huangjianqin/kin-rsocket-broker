package org.kin.rsocket.core;

import org.kin.framework.proxy.MethodDefinition;
import org.kin.framework.proxy.ProxyInvoker;
import org.kin.framework.proxy.Proxys;
import org.kin.framework.utils.ClassUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

/**
 * reactive service端method invoker
 *
 * @author huangjianqin
 * @date 2021/3/27
 */
class ReactiveMethodInvoker extends ReactiveMethodSupport {
    /** 服务实例代理 */
    private ProxyInvoker<?> invoker;
    /** 方法是否是异步返回 */
    private boolean asyncReturn;
    /** 方法是否是返回bytes */
    private boolean binaryReturn;
    /** 方法参数类型 */
    private Class<?>[] parametersType;

    ReactiveMethodInvoker(Method method, Object provider) {
        super(method);
        if (RSocketAppContext.ENHANCE) {
            this.invoker = Proxys.byteBuddy().enhanceMethod(new MethodDefinition<>(provider, method));
        } else {
            this.invoker = Proxys.reflection().enhanceMethod(new MethodDefinition<>(provider, method));
        }
        this.method = method;
        this.parametersType = this.method.getParameterTypes();
        if (Flux.class.isAssignableFrom(this.returnType) ||
                Mono.class.isAssignableFrom(this.returnType) ||
                CompletableFuture.class.isAssignableFrom(this.returnType)) {
            this.asyncReturn = true;
        }
        this.binaryReturn = this.inferredClassForReturn != null && ReactiveMethodSupport.BINARY_CLASS_LIST.contains(this.inferredClassForReturn);
    }

    /**
     * 目标方法调用
     */
    Object invoke(Object... args) throws Exception {
        return invoker.invoke(args);
    }

    //setter && getter
    Class<?>[] getParameterTypes() {
        return this.parametersType;
    }

    void setParametersType(Class<?>[] parametersType) {
        this.parametersType = parametersType;
    }

    Class<?> getInferredClassForParameter(int paramIndex) {
        return ClassUtils.getInferredClassForGeneric(method.getGenericParameterTypes()[paramIndex]);
    }

    boolean isAsyncReturn() {
        return asyncReturn;
    }

    void setAsyncReturn(boolean asyncReturn) {
        this.asyncReturn = asyncReturn;
    }

    boolean isBinaryReturn() {
        return this.binaryReturn;
    }
}