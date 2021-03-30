package org.kin.rsocket.core.discovery;

import org.springframework.cloud.client.ServiceInstance;
import reactor.core.publisher.Flux;

/**
 * 用于spring cloud discovery发现服务instance
 * 作为broker的一个常驻服务存在
 *
 * @author huangjianqin
 * @date 2021/3/25
 */
public interface DiscoveryService {
    /**
     * 指定服务的服务节点实例信息
     *
     * @param serviceId todo 看springcloud怎么定义
     */
    Flux<ServiceInstance> getInstances(String serviceId);

    /**
     * 返回所有服务
     */
    Flux<String> getAllServices();
}
