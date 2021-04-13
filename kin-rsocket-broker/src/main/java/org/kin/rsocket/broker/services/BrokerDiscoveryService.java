package org.kin.rsocket.broker.services;

import org.kin.rsocket.broker.ServiceResponder;
import org.kin.rsocket.broker.ServiceRouteTable;
import org.kin.rsocket.broker.ServiceRouter;
import org.kin.rsocket.core.RSocketService;
import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.discovery.DiscoveryService;
import org.kin.rsocket.core.discovery.RSocketServiceInstance;
import org.kin.rsocket.core.domain.AppStatus;
import org.kin.rsocket.core.metadata.AppMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author huangjianqin
 * @date 2021/3/30
 */
@RSocketService(DiscoveryService.class)
public class BrokerDiscoveryService implements DiscoveryService {
    @Autowired
    private ServiceRouter serviceRouter;
    @Autowired
    private ServiceRouteTable routeTable;

    @Override
    public Flux<ServiceInstance> getInstances(String serviceId) {
        return findServiceInstances(serviceId);
    }

    @Override
    public Flux<String> getAllServices() {
        return Flux.fromIterable(routeTable.getAllServices()).map(ServiceLocator::getGsv);
    }

    /**
     * 寻找service instances
     */
    private Flux<ServiceInstance> findServiceInstances(String routeKey) {
        Collection<Integer> instanceIds = routeTable.getAllInstanceIds(ServiceLocator.serviceHashCode(routeKey));
        if (instanceIds.isEmpty()) {
            return findServicesInstancesByAppName(routeKey);
        }
        List<ServiceInstance> serviceInstances = new ArrayList<>();
        for (Integer instanceId : instanceIds) {
            ServiceResponder responder = serviceRouter.getByInstanceId(instanceId);
            if (responder != null) {
                serviceInstances.add(newServiceInstance(responder));
            }
        }
        return Flux.fromIterable(serviceInstances);
    }

    /**
     * 通过app name寻找service instances
     */
    private Flux<ServiceInstance> findServicesInstancesByAppName(String appName) {
        return Flux.fromIterable(serviceRouter.getAllResponders())
                .filter(responder -> responder.getAppMetadata().getName().equalsIgnoreCase(appName))
                .filter(responder -> responder.getAppStatus().equals(AppStatus.SERVING))
                .map(this::newServiceInstance);
    }

    /**
     * 构建{@link RSocketServiceInstance}实例
     */
    private ServiceInstance newServiceInstance(ServiceResponder responder) {
        AppMetadata appMetadata = responder.getAppMetadata();
        RSocketServiceInstance serviceInstance = new RSocketServiceInstance();
        serviceInstance.setInstanceId(appMetadata.getUuid());
        serviceInstance.setServiceId(appMetadata.getName());
        serviceInstance.setHost(appMetadata.getIp());
        if (appMetadata.getWebPort() > 0) {
            serviceInstance.setPort(appMetadata.getWebPort());
            String schema = "http";
            serviceInstance.setSecure(appMetadata.isSecure());
            if (appMetadata.isSecure()) {
                schema = "https";
            }
            serviceInstance.setSchema(schema);
            serviceInstance.setUri(schema + "://" + appMetadata.getIp() + ":" + appMetadata.getWebPort());
        }
        serviceInstance.setMetadata(appMetadata.getMetadata());
        return serviceInstance;
    }

}
