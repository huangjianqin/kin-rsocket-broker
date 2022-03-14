package org.kin.rsocket.broker.services;

import org.kin.rsocket.broker.RSocketEndpoint;
import org.kin.rsocket.broker.RSocketServiceManager;
import org.kin.rsocket.broker.cluster.BrokerInfo;
import org.kin.rsocket.broker.cluster.RSocketBrokerManager;
import org.kin.rsocket.core.RSocketService;
import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.discovery.DiscoveryService;
import org.kin.rsocket.core.discovery.RSocketServiceInstance;
import org.kin.rsocket.core.domain.AppStatus;
import org.kin.rsocket.core.metadata.AppMetadata;
import org.kin.rsocket.core.utils.Symbols;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;

/**
 * @author huangjianqin
 * @date 2021/3/30
 */
@RSocketService(DiscoveryService.class)
public class DiscoveryServiceImpl implements DiscoveryService {
    @Autowired
    private RSocketServiceManager serviceManager;
    @Autowired
    private RSocketBrokerManager rsocketBrokerManager;

    @Override
    public Flux<RSocketServiceInstance> getInstances(String appName) {
        if (appName.equals(Symbols.BROKER)) {
            //支持查询broker集群信息
            return Flux.fromIterable(rsocketBrokerManager.all())
                    .filter(BrokerInfo::isActive)
                    .map(broker -> {
                        RSocketServiceInstance instance = new RSocketServiceInstance();
                        instance.setInstanceId(broker.getId());
                        instance.setHost(broker.getIp());
                        instance.setServiceId(Symbols.BROKER);
                        instance.setPort(broker.getPort());
                        instance.setSchema(broker.getSchema());
                        instance.setUri(broker.getUrl());
                        return instance;
                    });
        }
        return findServicesInstancesByAppName(appName);
    }

    @Override
    public Flux<String> getAllServices() {
        return Flux.fromIterable(serviceManager.getAllServices()).map(ServiceLocator::getGsv);
    }

    /**
     * 通过app name寻找service instances
     */
    private Flux<RSocketServiceInstance> findServicesInstancesByAppName(String appName) {
        return Flux.fromIterable(serviceManager.getByAppName(appName))
                .filter(responder -> responder.getAppStatus().equals(AppStatus.SERVING))
                .map(this::newServiceInstance);
    }

    /**
     * 构建{@link RSocketServiceInstance}实例
     */
    private RSocketServiceInstance newServiceInstance(RSocketEndpoint rsocketEndpoint) {
        AppMetadata appMetadata = rsocketEndpoint.getAppMetadata();
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
