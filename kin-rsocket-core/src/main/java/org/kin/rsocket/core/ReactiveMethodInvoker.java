package org.kin.rsocket.core;

import org.kin.framework.proxy.MethodDefinition;
import org.kin.framework.proxy.ProxyInvoker;
import org.kin.framework.proxy.Proxys;
import org.kin.framework.utils.ClassUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * reactive service端method invoker
 *
 * @author huangjianqin
 * @date 2021/3/27
 */
final class ReactiveMethodInvoker extends ReactiveMethodSupport {
    /** 服务实例代理 */
    private final ProxyInvoker<?> invoker;
    /** 方法是否是异步返回 */
    private final boolean asyncReturn;
    /** 方法是否是返回bytes */
    private final boolean binaryReturn;
    /** 方法参数类型 */
    private final Class<?>[] parametersTypes;
    /** 方法参数是否required or not */
    private final boolean[] required;

    @SuppressWarnings("unchecked")
    ReactiveMethodInvoker(Method method, Object provider) {
        super(method);

        if (RSocketAppContext.ENHANCE) {
            this.invoker = Proxys.byteBuddy().enhanceMethod(new MethodDefinition<>(provider, method));
        } else {
            this.invoker = Proxys.reflection().enhanceMethod(new MethodDefinition<>(provider, method));
        }

        if (Flux.class.isAssignableFrom(this.returnType) ||
                Mono.class.isAssignableFrom(this.returnType) ||
                CompletableFuture.class.isAssignableFrom(this.returnType)) {
            this.asyncReturn = true;
        } else {
            this.asyncReturn = false;
        }
        this.binaryReturn = this.inferredClassForReturn != null && BINARY_CLASS_LIST.contains(this.inferredClassForReturn);

        this.parametersTypes = this.method.getParameterTypes();

        Parameter[] parameters = method.getParameters();
        this.required = new boolean[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            required[i] = Objects.nonNull(parameter.getAnnotation(Required.class));
        }
    }

    /**
     * 目标方法调用
     */
    Object invoke(Object... args) throws Exception {
        int paramCount = getParamCount();
        if (args.length != paramCount) {
            throw new IllegalArgumentException(String.format("request params is not right! service method need %d params, not %d", paramCount, args.length));
        }

        //check required
        for (int i = 0; i < args.length; i++) {
            if (!required[i]) {
                continue;
            }

            boolean throwExt = false;
            Object arg = args[i];

            if (Objects.isNull(arg)) {
                throwExt = true;
            } else {
                Class<?> argClass = arg.getClass();
                if ((argClass.isPrimitive() ||
                        arg instanceof Number ||
                        arg instanceof String ||
                        arg instanceof Character ||
                        arg instanceof Boolean) && arg.equals(ClassUtils.getDefaultValue(argClass))) {
                    //基础类型
                    //不允许等于默认值
                    throwExt = true;
                }
            }
            if (throwExt) {
                throw new IllegalArgumentException(
                        String.format("param 'arg%s' of type %s is required! must not null or default value",
                                i, parametersTypes[i].getCanonicalName()));
            }
        }

        return invoker.invoke(args);
    }

    //getter
    Class<?>[] getParameterTypes() {
        return this.parametersTypes;
    }

    Class<?> getInferredClassForParameter(int paramIndex) {
        return ClassUtils.getInferredClassForGeneric(method.getGenericParameterTypes()[paramIndex]);
    }

    boolean isAsyncReturn() {
        return asyncReturn;
    }

    boolean isBinaryReturn() {
        return this.binaryReturn;
    }
}