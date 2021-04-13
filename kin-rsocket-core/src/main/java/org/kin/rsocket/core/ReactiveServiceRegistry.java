package org.kin.rsocket.core;

import org.kin.rsocket.core.domain.ReactiveServiceInfo;

import java.util.Set;

/**
 * 服务注册表
 * <p>
 * handlerName = 可以是方法名, 也可以是自定义名字
 * handlerId = hash(serviceName.handlerName)
 *
 * @author huangjianqin
 * @date 2021/3/27
 */
public interface ReactiveServiceRegistry {
    /**
     * 是否包含对应service, handlerName注册信息
     */
    boolean contains(String serviceName, String handlerName);

    /**
     * 是否包含对应service注册信息
     */
    boolean contains(String serviceName);

    /**
     * 是否包含对应serviceId注册信息
     */
    boolean contains(Integer serviceId);

    /**
     * 返回所有已注册的服务
     *
     * @return 所有已注册的服务
     */
    Set<String> findAllServices();

    /**
     * 返回所有已注册的服务
     *
     * @return 所有已注册的服务
     */
    Set<ServiceLocator> findAllServiceLocators();

    /**
     * 注册service
     */
    void addProvider(String group, String serviceName, String version, Class<?> serviceInterface, Object provider);

    /**
     * 注销service信息
     */
    void removeProvider(String group, String serviceName, String version, Class<?> serviceInterface);

    /**
     * 返回method invoker
     */
    ReactiveMethodInvoker getInvoker(String serviceName, String handlerName);

    /**
     * 返回method invoker
     */
    ReactiveMethodInvoker getInvoker(Integer handlerId);

    /**
     * 是否包含指定method invoker
     */
    boolean containsHandler(Integer handlerId);

    /**
     * 用于后台访问服务接口具体信息
     */
    ReactiveServiceInfo getReactiveServiceInfoByName(String serviceName);
}
