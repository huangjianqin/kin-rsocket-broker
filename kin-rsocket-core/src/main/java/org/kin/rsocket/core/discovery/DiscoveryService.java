package org.kin.rsocket.core.discovery;

import org.springframework.cloud.client.ServiceInstance;
import reactor.core.publisher.Flux;

/**
 * 用于spring cloud discovery发现服务instance
 *
 * @author huangjianqin
 * @date 2021/3/25
 */
public interface DiscoveryService {
    Flux<ServiceInstance> getInstances(String serviceId);

    Flux<String> getAllServices();
}
