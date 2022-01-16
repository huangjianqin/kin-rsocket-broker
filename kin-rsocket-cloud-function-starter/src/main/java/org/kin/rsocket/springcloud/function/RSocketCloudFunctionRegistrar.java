package org.kin.rsocket.springcloud.function;

import org.kin.framework.utils.ClassUtils;
import org.kin.framework.utils.ExceptionUtils;
import org.kin.rsocket.core.ReactiveMethodInvoker;
import org.kin.rsocket.core.domain.RSocketServiceInfo;
import org.kin.rsocket.core.domain.ReactiveMethodInfo;
import org.kin.rsocket.core.domain.ReactiveMethodParameterInfo;
import org.kin.rsocket.service.RSocketServiceProperties;
import org.kin.rsocket.service.RSocketServiceRequester;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.Order;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 解析cloud function注册中心, 然后将其service注册到自己定义的注册中心去
 *
 * @author huangjianqin
 * @date 2021/5/21
 */
@Order(99)
public class RSocketCloudFunctionRegistrar implements ApplicationListener<ApplicationStartedEvent> {
    @Autowired
    private FunctionRegistry functionRegistry;
    @Autowired
    private RSocketServiceProperties serviceConfig;
    @Autowired
    private RSocketServiceRequester requester;

    @Override
    public void onApplicationEvent(@Nonnull ApplicationStartedEvent event) {
        //所有function name
        //function name = service name + '.' + method
        Set<String> functionNames = functionRegistry.getNames(null).stream()
                .filter(functionName -> functionName.contains("."))
                .collect(Collectors.toSet());

        for (String functionName : functionNames) {
            //找到cloud function invoke包装类
            SimpleFunctionRegistry.FunctionInvocationWrapper function = functionRegistry.lookup(functionName);
            String service = functionName.substring(0, functionName.lastIndexOf("."));
            String handler = functionName.substring(functionName.lastIndexOf(".") + 1);

            try {
                //寻找方法
                Method method = getMethod(function);
                if (Objects.isNull(method)) {
                    continue;
                }

                //将其封装成invoker
                Type outputType = function.getOutputType();
                ReactiveMethodInvoker invoker = new ReactiveMethodInvoker(
                        method, function,
                        function.getRawOutputType(), outputType,
                        new Class[]{function.getRawInputType()});
                List<ReactiveMethodParameterInfo> methodParameterInfos = new ArrayList<>(1);
                if (function.isFunction() || function.isConsumer()) {
                    Type inputType = function.getInputType();
                    Class<?> inferredClassForInput = ClassUtils.getInferredClassForGeneric(inputType);
                    //Supplier没有参数
                    methodParameterInfos.add(ReactiveMethodParameterInfo.builder()
                            .name("arg")
                            .type(function.getRawInputType().getName())
                            .inferredType(inferredClassForInput.getName())
                            .build());
                }

                Class<?> inferredClassForOutput = ClassUtils.getInferredClassForGeneric(outputType);
                ReactiveMethodInfo reactiveMethodInfo = ReactiveMethodInfo.builder()
                        .name(handler)
                        .description(function.getFunctionDefinition())
                        .returnType(function.getRawInputType().getName())
                        .returnInferredType(inferredClassForOutput.getName())
                        .parameters(methodParameterInfos)
                        .build();

                RSocketServiceInfo rsocketServiceInfo = RSocketServiceInfo.builder()
                        .name(service)
                        .service(service)
                        .group(serviceConfig.getGroup())
                        .version(serviceConfig.getVersion())
                        .description(function.getFunctionDefinition())
                        .methods(Collections.singletonList(reactiveMethodInfo))
                        .build();

                requester.registerService(handler, function, invoker, rsocketServiceInfo);
            } catch (Exception e) {
                ExceptionUtils.throwExt(e);
            }
        }
    }

    /**
     * 根据{@link SimpleFunctionRegistry.FunctionInvocationWrapper}类型获取对应的{@link Method}
     */
    private Method getMethod(SimpleFunctionRegistry.FunctionInvocationWrapper function) {
        try {
            if (function.isFunction()) {
                return function.getClass().getMethod("apply", Object.class);
            } else if (function.isConsumer()) {
                return function.getClass().getMethod("accept", Object.class);
            } else if (function.isSupplier()) {
                return function.getClass().getMethod("get");
            }
        } catch (Exception e) {
            ExceptionUtils.throwExt(e);
        }
        return null;
    }
}
