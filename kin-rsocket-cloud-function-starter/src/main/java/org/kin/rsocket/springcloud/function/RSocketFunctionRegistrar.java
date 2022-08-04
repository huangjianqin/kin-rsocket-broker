package org.kin.rsocket.springcloud.function;

import org.kin.framework.spring.beans.BeanDefinitionUtils;
import org.kin.framework.utils.ClassUtils;
import org.kin.framework.utils.CollectionUtils;
import org.kin.framework.utils.ExceptionUtils;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.core.Desc;
import org.kin.rsocket.core.LocalRSocketServiceRegistry;
import org.kin.rsocket.core.ReactiveMethodInvoker;
import org.kin.rsocket.core.domain.RSocketServiceInfo;
import org.kin.rsocket.core.domain.ReactiveMethodInfo;
import org.kin.rsocket.core.domain.ReactiveMethodParameterInfo;
import org.kin.rsocket.service.RSocketServiceProperties;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.cloud.function.context.FunctionRegistry;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.Order;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
public class RSocketFunctionRegistrar implements ApplicationListener<ApplicationStartedEvent>, BeanFactoryAware {
    @Autowired
    private FunctionRegistry functionRegistry;
    @Autowired
    private RSocketServiceProperties serviceConfig;

    private ConfigurableListableBeanFactory beanFactory;

    @Override
    public void onApplicationEvent(@Nonnull ApplicationStartedEvent event) {
        //所有function name, 即bean name
        //function name = service name + '.' + handler
        Set<String> functionNames = functionRegistry.getNames(null).stream()
                .filter(functionName -> functionName.contains("."))
                .collect(Collectors.toSet());

        for (String functionName : functionNames) {
            //找到cloud function invoke包装类
            SimpleFunctionRegistry.FunctionInvocationWrapper function = functionRegistry.lookup(functionName);
            //默认hanlder name
            String handler = functionName.substring(functionName.lastIndexOf(".") + 1);

            try {
                //寻找function方法
                Method method = getMethod(function);
                //构建rsocket service invoker
                Type outputType = function.getOutputType();

                RSocketServiceInfo rsocketServiceInfo = newReactiveServiceInfo(functionName, handler, function);
                if (Objects.isNull(rsocketServiceInfo)) {
                    continue;
                }

                ReactiveMethodInvoker invoker = new ReactiveMethodInvoker(
                        method, function, function.getRawOutputType(), outputType,
                        new Class[]{function.getRawInputType()});
                LocalRSocketServiceRegistry.INSTANCE.addProvider(handler, function, invoker,
                        rsocketServiceInfo);
            } catch (Exception e) {
                ExceptionUtils.throwExt(e);
            }
        }
    }

    /**
     * 根据function定义生成rsocket service信息, 即{@link RSocketServiceInfo}实例
     */
    @Nullable
    private RSocketServiceInfo newReactiveServiceInfo(String functionName, String handler,
                                                      SimpleFunctionRegistry.FunctionInvocationWrapper function) {
        //获取@RSocketFunction注解属性
        AnnotationAttributes rsocketFunctionAnnoAttrs = BeanDefinitionUtils.getBeanFactoryMethodAnnoAttributes(beanFactory, functionName, RSocketFunction.class);
        if (rsocketFunctionAnnoAttrs.isEmpty()) {
            //没有使用@RSocketFunction注解
            return null;
        }

        //默认service name
        String service = functionName.substring(0, functionName.lastIndexOf("."));
        //默认group
        String group = serviceConfig.getGroup();
        //默认version
        String version = serviceConfig.getVersion();
        //默认tags
        String[] tags = new String[0];
        //默认function描述
        String description = function.getFunctionDefinition();

        //用@RSocketFunction注解定义覆盖function定义
        String annoGroup = rsocketFunctionAnnoAttrs.getString("group");
        if (StringUtils.isNotBlank(annoGroup)) {
            //overwrite
            group = annoGroup;
        }
        String annoVersion = rsocketFunctionAnnoAttrs.getString("version");
        if (StringUtils.isNotBlank(annoVersion)) {
            //overwrite
            version = annoVersion;
        }
        String[] annoTags = rsocketFunctionAnnoAttrs.getStringArray("tags");
        if (CollectionUtils.isNonEmpty(annoTags)) {
            tags = annoTags;
        }

        //reactive function method param属性
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

        //reactive function method属性
        Type outputType = function.getOutputType();
        Class<?> inferredClassForOutput = ClassUtils.getInferredClassForGeneric(outputType);
        ReactiveMethodInfo reactiveMethodInfo = ReactiveMethodInfo.builder()
                .name(handler)
                .description(description)
                .returnType(function.getRawOutputType().getName())
                .returnInferredType(inferredClassForOutput.getName())
                .parameters(methodParameterInfos)
                .build();

        //默认function对应rsocket service描述
        String serviceDescription = description;
        AnnotationAttributes descAnnoAttrs = BeanDefinitionUtils.getBeanFactoryMethodAnnoAttributes(beanFactory, functionName, Desc.class);
        if (!descAnnoAttrs.isEmpty()) {
            String descAnnoServiceDescription = descAnnoAttrs.getString("value");
            if (StringUtils.isNotBlank(descAnnoServiceDescription)) {
                serviceDescription = descAnnoServiceDescription;
            }
        }

        return RSocketServiceInfo.builder()
                .name(service)
                .service(service)
                .group(group)
                .version(version)
                .tags(tags)
                .description(serviceDescription)
                .deprecated(BeanDefinitionUtils.isBeanFactoryMethodAnnotated(beanFactory, functionName, Deprecated.class))
                .methods(Collections.singletonList(reactiveMethodInfo))
                .build();
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
        throw new IllegalArgumentException("function bean instance is not a functional interface");
    }

    @Override
    public void setBeanFactory(@Nonnull BeanFactory beanFactory) throws BeansException {
        this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
    }
}
