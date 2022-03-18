package org.kin.spring.rsocket.support;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author huangjianqin
 * @date 2022/3/15
 */
@Endpoint(id = "springRSocketServiceDiscoveryRegistry")
public final class SpringRSocketServiceDiscoveryRegistryEndpoint {
    private final SpringRSocketServiceDiscoveryRegistry registry;

    public SpringRSocketServiceDiscoveryRegistryEndpoint(SpringRSocketServiceDiscoveryRegistry registry) {
        this.registry = registry;
    }

    @ReadOperation
    public Map<String, Object> info() {
        Map<String, Object> info = new HashMap<>();
        Map<String, List<String>> services = new HashMap<>();
        for (Map.Entry<String, List<RSocketServiceInstance>> entry : registry.getServiceInstances().entrySet()) {
            services.put(entry.getKey(), entry.getValue().stream().map(RSocketServiceInstance::getURI).collect(Collectors.toList()));
        }
        info.put("services", services);
        info.put("lastRefreshAt", registry.getLastRefreshTimeMs());
        return info;
    }
}
