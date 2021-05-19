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
     * {@link RSocketServiceManager#lock}加锁下完成
     */
    Integer route(int serviceId);

    /**
     * app注册完触发
     * {@link RSocketServiceManager#lock}加锁下完成
     */
    void onAppRegistered(int instanceId, int weight, Collection<ServiceLocator> services);

    /**
     * app注销完触发
     * {@link RSocketServiceManager#lock}加锁下完成
     */
    void onServiceUnregistered(int instanceId, Collection<Integer> serviceIds);

    /**
     * 获取所有指定服务对应的所有app instance Id
     * {@link RSocketServiceManager#lock}加锁下完成
     */
    Collection<Integer> getAllInstanceIds(int serviceId);
}
