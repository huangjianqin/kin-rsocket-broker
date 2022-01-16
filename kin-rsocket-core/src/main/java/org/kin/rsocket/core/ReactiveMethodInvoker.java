package org.kin.rsocket.core;

import org.kin.framework.proxy.MethodDefinition;
import org.kin.framework.proxy.ProxyInvoker;
import org.kin.framework.proxy.Proxys;
import org.kin.framework.utils.ClassUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;

/**
 * reactive service端method invoker
 *
 * @author huangjianqin
 * @date 2021/3/27
 */
public final class ReactiveMethodInvoker extends ReactiveMethodSupport {
    /** 服务实例代理 */
    private final ProxyInvoker<?> invoker;
    /** 方法是否是异步返回 */
    private boolean asyncReturn;
    /** 方法是否是返回bytes */
    private boolean binaryReturn;
    /** 方法参数类型 */
    private Class<?>[] parametersTypes;

    public ReactiveMethodInvoker(Method method, Object provider) {
        super(method);

        if (RSocketAppContext.ENHANCE) {
            this.invoker = Proxys.byteBuddy().enhanceMethod(new MethodDefinition<>(provider, method));
        } else {
            this.invoker = Proxys.reflection().enhanceMethod(new MethodDefinition<>(provider, method));
        }

        initReturn();

        this.parametersTypes = this.method.getParameterTypes();
    }

    /**
     * 供cloud function使用, 其使用了wrapper封装, 无法从provider和method那里获取到具体function信息
     */
    public ReactiveMethodInvoker(Method method, Object provider, Class<?> rawReturnType, Type returnType, Class<?>[] parametersTypes) {
        this(method, provider);
        super.returnType = rawReturnType;
        super.inferredClassForReturn = ClassUtils.getInferredClassForGeneric(returnType);

        initReturn();
        this.parametersTypes = parametersTypes;
    }

    private void initReturn() {
        this.asyncReturn = Flux.class.isAssignableFrom(this.returnType) ||
                Mono.class.isAssignableFrom(this.returnType) ||
                CompletableFuture.class.isAssignableFrom(this.returnType);
        this.binaryReturn = this.inferredClassForReturn != null && BINARY_CLASS_LIST.contains(this.inferredClassForReturn);
    }

    /**
     * 目标方法调用
     */
    public Object invoke(Object... args) throws Exception {
        int paramCount = getParamCount();
        if (args.length != paramCount) {
            throw new IllegalArgumentException(String.format("request params is not right! service method need %d params, not %d", paramCount, args.length));
        }

        return invoker.invoke(args);
    }

    //getter
    public Class<?>[] getParameterTypes() {
        return this.parametersTypes;
    }

    public Class<?> getInferredClassForParameter(int paramIndex) {
        return ClassUtils.getInferredClassForGeneric(method.getGenericParameterTypes()[paramIndex]);
    }

    public boolean isAsyncReturn() {
        return asyncReturn;
    }

    public boolean isBinaryReturn() {
        return this.binaryReturn;
    }
}