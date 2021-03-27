package org.kin.rsocket.core;

import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.core.utils.MurmurHash3;
import org.kin.rsocket.core.utils.Separators;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * todo 后续考虑增加返回服务接口信息方法, 参考阿里ReactiveServiceInterface
 *
 * @author huangjianqin
 * @date 2021/3/27
 */
public class DefaultReactiveServiceRegistry implements ReactiveServiceRegistry {
    /** key -> serviceName, value -> provider, 即service instance */
    private final Map<String, Object> serviceName2Provider = new HashMap<>();
    /** key -> hash(serviceName.method), value -> service method invoker */
    private final Map<Integer, ReactiveMethodInvoker> handlerId2Invoker = new HashMap<>();

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
    public void addProvider(String group, String serviceName, String version, Class<?> serviceInterface, Object provider) {
        for (Method method : serviceInterface.getMethods()) {
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
            }
        }
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
}
