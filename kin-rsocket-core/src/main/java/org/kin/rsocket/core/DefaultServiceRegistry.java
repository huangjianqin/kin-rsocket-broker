package org.kin.rsocket.core;

import org.kin.framework.utils.ClassUtils;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.core.domain.ReactiveMethodInfo;
import org.kin.rsocket.core.domain.ReactiveMethodParameterInfo;
import org.kin.rsocket.core.domain.ReactiveServiceInfo;
import org.kin.rsocket.core.utils.MurmurHash3;
import org.kin.rsocket.core.utils.Separators;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * todo 后续考虑增加返回服务接口信息方法, 参考阿里ReactiveServiceInterface
 *
 * @author huangjianqin
 * @date 2021/3/27
 */
public class DefaultServiceRegistry implements ReactiveServiceRegistry {
    /** key -> serviceName, value -> provider, 即service instance */
    private final Map<String, Object> serviceName2Provider = new HashMap<>();
    /** key -> hash(serviceName.method), value -> service method invoker */
    private final Map<Integer, ReactiveMethodInvoker> handlerId2Invoker = new HashMap<>();
    /** key -> service name, value -> reactive service info */
    protected Map<String, ReactiveServiceInfo> serviceName2Info = new HashMap<>();

    public DefaultServiceRegistry() {
        addProvider("", ReactiveServiceRegistry.class.getCanonicalName(), "", ReactiveServiceRegistry.class, this);
    }

    @Override
    public boolean contains(Integer serviceId) {
        return handlerId2Invoker.containsKey(serviceId);
    }

    @Override
    public boolean contains(String serviceName, String handlerName) {
        return contains(MurmurHash3.hash32(serviceName + Separators.SERVICE_HANDLER + handlerName));
    }

    @Override
    public boolean contains(String serviceName) {
        return serviceName2Provider.containsKey(serviceName);
    }

    @Override
    public Set<String> findAllServices() {
        return serviceName2Provider.keySet();
    }

    @Override
    public Set<ServiceLocator> findAllServiceLocators() {
        return serviceName2Info.values().stream()
                .map(i -> new ServiceLocator(i.getGroup(), i.getServiceName(), i.getVersion()))
                .collect(Collectors.toSet());
    }

    @Override
    public void addProvider(String group, String serviceName, String version, Class<?> interfaceClass, Object provider) {
        for (Method method : interfaceClass.getMethods()) {
            if (!method.isDefault()) {
                String handlerName = method.getName();
                //解析ServiceMapping注解, 看看是否自定义method(handler) name
                ServiceMapping serviceMapping = method.getAnnotation(ServiceMapping.class);
                if (Objects.nonNull(serviceMapping) && StringUtils.isNotBlank(serviceMapping.value())) {
                    handlerName = serviceMapping.value();
                }
                String key = serviceName + Separators.SERVICE_HANDLER + handlerName;

                ReactiveMethodInvoker invoker = new ReactiveMethodInvoker(method, provider);

                serviceName2Provider.put(serviceName, provider);
                handlerId2Invoker.put(MurmurHash3.hash32(key), invoker);
                serviceName2Info.put(serviceName, newReactiveServiceInfo(group, serviceName, version, interfaceClass));
            }
        }
    }

    /**
     * 创建reactive service信息, 用于后台访问服务接口具体信息
     */
    private ReactiveServiceInfo newReactiveServiceInfo(String group, String serviceName,
                                                       String version, Class<?> interfaceClass) {
        ReactiveServiceInfo serviceInfo = new ReactiveServiceInfo();
        serviceInfo.setGroup(group);
        serviceInfo.setVersion(version);
        if (interfaceClass.getPackage() != null) {
            serviceInfo.setNamespace(interfaceClass.getPackage().getName());
        }
        serviceInfo.setName(interfaceClass.getName());
        serviceInfo.setServiceName(serviceName);
        Deprecated interfaceDeprecated = interfaceClass.getAnnotation(Deprecated.class);
        if (interfaceDeprecated != null) {
            serviceInfo.setDeprecated(true);
        }

        List<ReactiveMethodInfo> methodInfos = new ArrayList<>(8);
        for (Method method : interfaceClass.getMethods()) {
            if (method.isDefault() || Modifier.isStatic(method.getModifiers())) {
                //过滤掉default 和 static 方法
                continue;
            }

            methodInfos.add(newReactiveMethodInfo(method));
        }
        serviceInfo.setOperations(methodInfos);

        return serviceInfo;
    }

    /**
     * 创建reactive service method信息
     */
    private ReactiveMethodInfo newReactiveMethodInfo(Method method) {
        ReactiveMethodInfo methodInfo = new ReactiveMethodInfo();
        Deprecated methodDeprecated = method.getAnnotation(Deprecated.class);
        if (methodDeprecated != null) {
            methodInfo.setDeprecated(true);
        }
        methodInfo.setName(method.getName());
        methodInfo.setReturnType(method.getReturnType().getCanonicalName());
        methodInfo.setReturnInferredType(ClassUtils.getInferredClassForGeneric(method.getGenericReturnType()).getCanonicalName());

        List<ReactiveMethodParameterInfo> parameterInfos = new ArrayList<>(4);
        for (Parameter parameter : method.getParameters()) {

        }
        methodInfo.setParameters(parameterInfos);

        return methodInfo;
    }

    /**
     * 创建reactive service parameter信息
     */
    private ReactiveMethodParameterInfo newReactiveMethodParameterInfo(Parameter parameter) {
        ReactiveMethodParameterInfo parameterInfo = new ReactiveMethodParameterInfo();
        parameterInfo.setName(parameter.getName());
        parameterInfo.setType(parameter.getType().getCanonicalName());
        String inferredType = ClassUtils.getInferredClassForGeneric(parameter.getParameterizedType()).getCanonicalName();
        if (!parameterInfo.getType().equals(inferredType)) {
            parameterInfo.setInferredType(inferredType);
        }

        return parameterInfo;
    }

    @Override
    public void removeProvider(String group, String serviceName, String version, Class<?> serviceInterface) {
        serviceName2Provider.remove(serviceName);
        for (Method method : serviceInterface.getMethods()) {
            if (!method.isDefault()) {
                String handlerName = method.getName();
                ServiceMapping serviceMapping = method.getAnnotation(ServiceMapping.class);
                if (Objects.nonNull(serviceMapping) && StringUtils.isNotBlank(serviceMapping.value())) {
                    handlerName = serviceMapping.value();
                }
                String key = serviceName + "." + handlerName;

                handlerId2Invoker.remove(MurmurHash3.hash32(key));
                serviceName2Info.remove(serviceName);
            }
        }
    }

    @Override
    public ReactiveMethodInvoker getInvoker(String serviceName, String handlerName) {
        return getInvoker(MurmurHash3.hash32(serviceName + Separators.SERVICE_HANDLER + handlerName));
    }

    @Override
    public ReactiveMethodInvoker getInvoker(Integer handlerId) {
        return handlerId2Invoker.get(handlerId);
    }

    @Override
    public boolean containsHandler(Integer handlerId) {
        return handlerId2Invoker.containsKey(handlerId);
    }

    @Override
    public ReactiveServiceInfo getReactiveServiceInfoByName(String serviceName) {
        return serviceName2Info.get(serviceName);
    }
}
