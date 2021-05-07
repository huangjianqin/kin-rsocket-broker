package org.kin.rsocket.broker;

import org.kin.rsocket.core.ServiceLocator;

import java.util.Collection;

/**
 * 路由规则
 *
 * @author huangjianqin
 * @date 2021/5/7
 */
public interface Router {
    /**
     * 根据实现的路由规则选择出一个app instance Id
     * {@link ServiceManager#lock}加锁下完成
     */
    Integer route(Integer serviceId);

    /**
     * app注册完触发
     * {@link ServiceManager#lock}加锁下完成
     */
    void onAppRegistered(Integer instanceId, int weight, Collection<ServiceLocator> services);

    /**
     * app注销完触发
     * {@link ServiceManager#lock}加锁下完成
     */
    void onServiceUnregistered(Integer instanceId, Collection<Integer> serviceIds);

    /**
     * 获取所有指定服务对应的所有app instance Id
     * {@link ServiceManager#lock}加锁下完成
     */
    Collection<Integer> getAllInstanceIds(Integer serviceId);
}
