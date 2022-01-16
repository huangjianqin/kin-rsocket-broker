package org.kin.rsocket.springcloud.function;

import org.kin.framework.utils.ClassUtils;
import org.kin.framework.utils.ExceptionUtils;
import org.kin.rsocket.core.RSocketServiceRegistry;
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
import java.util.Collections;
import java.util.Set;
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
            try {
                // TODO: 2022/1/16 处理其他类型的function
                //寻找其apply方法
                Method method = function.getClass().getMethod("apply", Object.class);
                String service = functionName.substring(0, functionName.lastIndexOf("."));
                String handler = functionName.substring(functionName.lastIndexOf(".") + 1);

                //将其封装成invoker
                Type outputType = function.getOutputType();
                Class<?> inferredClassForOutput = ClassUtils.getInferredClassForGeneric(outputType);
                ReactiveMethodInvoker invoker = new ReactiveMethodInvoker(
                        method, function,
                        function.getRawOutputType(), outputType,
                        new Class[]{function.getRawInputType()});

                Type inputType = function.getInputType();
                Class<?> inferredClassForInput = ClassUtils.getInferredClassForGeneric(inputType);

                ReactiveMethodParameterInfo methodParameterInfo = ReactiveMethodParameterInfo.builder()
                        .name("arg")
                        .type(function.getRawInputType().getName())
                        .inferredType(inferredClassForInput.getName())
                        .build();

                ReactiveMethodInfo reactiveMethodInfo = ReactiveMethodInfo.builder()
                        .name(handler)
                        .description(function.getFunctionDefinition())
                        .returnType(function.getRawInputType().getName())
                        .returnInferredType(inferredClassForOutput.getName())
                        .parameters(Collections.singletonList(methodParameterInfo))
                        .build();

                RSocketServiceInfo rsocketServiceInfo = RSocketServiceInfo.builder()
                        .name(service)
                        .service(service)
                        .group(serviceConfig.getGroup())
                        .version(serviceConfig.getVersion())
                        .description(function.getFunctionDefinition())
                        .methods(Collections.singletonList(reactiveMethodInfo))
                        .build();

                RSocketServiceRegistry.INSTANCE.addProvider(service, handler, function, invoker, rsocketServiceInfo);
            } catch (Exception e) {
                ExceptionUtils.throwExt(e);
            }
        }
    }
}
