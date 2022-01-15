package org.kin.rsocket.broker;

import io.netty.buffer.ByteBuf;
import org.kin.rsocket.core.ServiceLocator;

import java.util.Collection;

/**
 * provider路由规则
 *
 * @author huangjianqin
 * @date 2021/5/7
 */
public interface ProviderRouter {
    /**
     * 根据实现的路由规则选择出一个app instance Id
     *
     * @param serviceId  服务gsv
     * @param paramBytes 请求的参数序列化后bytes
     */
    Integer route(int serviceId, ByteBuf paramBytes);

    /**
     * app注册完触发
     * {@link RSocketServiceManager#lock}加锁下完成
     */
    void onAppRegistered(BrokerResponder responder, int weight, Collection<ServiceLocator> services);

    /**
     * app注销完触发
     * {@link RSocketServiceManager#lock}加锁下完成
     */
    void onServiceUnregistered(BrokerResponder responder, int weight, Collection<Integer> serviceIds);

    /**
     * 获取所有指定服务对应的所有app instance Id
     * {@link RSocketServiceManager#lock}加锁下完成
     */
    Collection<Integer> getAllInstanceIds(int serviceId);
}
