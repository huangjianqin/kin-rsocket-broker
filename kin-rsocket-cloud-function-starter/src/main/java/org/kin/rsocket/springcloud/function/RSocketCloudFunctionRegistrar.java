package org.kin.rsocket.springcloud.function;

import org.kin.framework.utils.ExceptionUtils;
import org.kin.rsocket.core.RSocketServiceRegistry;
import org.kin.rsocket.core.ReactiveMethodInvoker;
import org.kin.rsocket.core.domain.RSocketServiceInfo;
import org.kin.rsocket.service.RSocketServiceProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 解析cloud function注册中心, 然后将其service注册到自己定义的注册中心去
 *
 * @author huangjianqin
 * @date 2021/5/21
 */
@Order(-99)
public class RSocketCloudFunctionRegistrar implements ApplicationListener<ApplicationReadyEvent> {
    @Autowired
    private FunctionRegistry functionRegistry;
    @Autowired
    private RSocketServiceProperties serviceConfig;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        //所有function name
        //function name = service name + '.' + method
        Set<String> functionNames = functionRegistry.getNames(null).stream()
                .filter(functionName -> functionName.contains("."))
                .collect(Collectors.toSet());

        for (String functionName : functionNames) {
            //找到cloud function invoke包装类
            SimpleFunctionRegistry.FunctionInvocationWrapper function = functionRegistry.lookup(functionName);
            try {
                //寻找其apply方法
                Method method = function.getClass().getMethod("apply", Object.class);
                String serviceName = functionName.substring(0, functionName.lastIndexOf("."));
                String handlerName = functionName.substring(functionName.lastIndexOf(".") + 1, functionName.length());

                //将其封装成invoker
                ReactiveMethodInvoker invoker = new ReactiveMethodInvoker(
                        method, function,
                        function.getRawOutputType(), function.getOutputType(),
                        new Class[]{function.getRawInputType()});

                RSocketServiceInfo.Builder builder = RSocketServiceInfo.builder();
                RSocketServiceInfo rsocketServiceInfo = builder.name(serviceName).serviceName(serviceName)
                        .group(serviceConfig.getGroup()).version(serviceConfig.getVersion())
                        .description(function.getFunctionDefinition()).build();

                RSocketServiceRegistry.INSTANCE.addProvider(serviceName, handlerName, function, invoker, rsocketServiceInfo);
            } catch (Exception e) {
                ExceptionUtils.throwExt(e);
            }
        }
    }
}
