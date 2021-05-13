package org.kin.rsocket.broker.services;

import org.kin.rsocket.broker.BrokerResponder;
import org.kin.rsocket.broker.ServiceManager;
import org.kin.rsocket.core.RSocketService;
import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.discovery.DiscoveryService;
import org.kin.rsocket.core.discovery.RSocketServiceInstance;
import org.kin.rsocket.core.domain.AppStatus;
import org.kin.rsocket.core.metadata.AppMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;

/**
 * @author huangjianqin
 * @date 2021/3/30
 */
@RSocketService(DiscoveryService.class)
public class BrokerDiscoveryService implements DiscoveryService {
    @Autowired
    private ServiceManager serviceManager;

    @Override
    public Flux<RSocketServiceInstance> getInstances(String serviceId) {
        return findServicesInstancesByAppName(serviceId);
    }

    @Override
    public Flux<String> getAllServices() {
        return Flux.fromIterable(serviceManager.getAllServices()).map(ServiceLocator::getGsv);
    }

    /**
     * 通过app name寻找service instances
     */
    private Flux<RSocketServiceInstance> findServicesInstancesByAppName(String appName) {
        return Flux.fromIterable(serviceManager.getAllResponders())
                .filter(responder -> responder.getAppMetadata().getName().equalsIgnoreCase(appName))
                .filter(responder -> responder.getAppStatus().equals(AppStatus.SERVING))
                .map(this::newServiceInstance);
    }

    /**
     * 构建{@link RSocketServiceInstance}实例
     */
    private RSocketServiceInstance newServiceInstance(BrokerResponder responder) {
        AppMetadata appMetadata = responder.getAppMetadata();
        RSocketServiceInstance serviceInstance = new RSocketServiceInstance();
        serviceInstance.setInstanceId(appMetadata.getUuid());
        serviceInstance.setServiceId(appMetadata.getName());
        serviceInstance.setHost(appMetadata.getIp());
        if (appMetadata.getWebPort() > 0) {
            serviceInstance.setPort(appMetadata.getWebPort());
            String schema = "http";
            serviceInstance.setSecure(appMetadata.isSecure());
            //todo 返回https时, http gate抛异常
//            if (appMetadata.isSecure()) {
//                schema = "https";
//            }
            serviceInstance.setSchema(schema);
            //返回web port
            serviceInstance.setUri(schema + "://" + appMetadata.getIp() + ":" + appMetadata.getWebPort());
        }
        serviceInstance.setMetadata(appMetadata.getMetadata());
        return serviceInstance;
    }

}
