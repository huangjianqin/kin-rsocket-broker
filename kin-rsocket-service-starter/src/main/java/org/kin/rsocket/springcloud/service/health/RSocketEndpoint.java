package org.kin.rsocket.springcloud.service.health;

import org.kin.rsocket.core.*;
import org.kin.rsocket.core.domain.AppStatus;
import org.kin.rsocket.core.event.AppStatusEvent;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.RSocketServicesExposedEvent;
import org.kin.rsocket.core.event.RSocketServicesHiddenEvent;
import org.kin.rsocket.core.health.HealthCheck;
import org.kin.rsocket.service.RSocketServiceProperties;
import org.kin.rsocket.service.RSocketServiceReferenceBuilder;
import org.kin.rsocket.service.UpstreamClusterManager;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 用于actuator获取监控信息, 或者控制application 行为
 *
 * @author huangjianqin
 * @date 2021/3/28
 */
@Endpoint(id = "rsocket")
public final class RSocketEndpoint {
    private final RSocketServiceProperties rsocketServiceProperties;
    private final UpstreamClusterManager upstreamClusterManager;
    private final boolean serviceProvider;
    /** app status */
    private AppStatus serviceStatus = AppStatus.SERVING;
    /** 下线的服务 */
    private final Set<String> offlineServices = new HashSet<>();

    public RSocketEndpoint(RSocketServiceProperties rsocketServiceProperties,
                           UpstreamClusterManager upstreamClusterManager) {
        this.rsocketServiceProperties = rsocketServiceProperties;
        this.upstreamClusterManager = upstreamClusterManager;
        Set<ServiceLocator> exposedServices = RSocketServiceRegistry.exposedServices();
        this.serviceProvider = !exposedServices.isEmpty();
    }

    @ReadOperation
    public Map<String, Object> info() {
        Map<String, Object> info = new HashMap<>();
        info.put("id", RSocketAppContext.ID);
        info.put("serviceStatus", serviceStatus.getDesc());
        if (this.serviceProvider) {
            info.put("published", RSocketServiceRegistry.exposedServices());
        }
        if (!RSocketServiceReferenceBuilder.CONSUMED_SERVICES.isEmpty()) {
            info.put("subscribed", RSocketServiceReferenceBuilder.CONSUMED_SERVICES.stream()
                    //过滤掉自带的服务
                    .filter(serviceLocator -> !HealthCheck.class.getCanonicalName().equals(serviceLocator.getService()))
                    .collect(Collectors.toList()));
        }
        //service upstream
        Collection<UpstreamCluster> upstreamClusters = upstreamClusterManager.getAll();
        if (!upstreamClusters.isEmpty()) {
            info.put("upstreams", upstreamClusters.stream().map(upstreamCluster -> {
                Map<String, Object> temp = new HashMap<>();
                temp.put("service", upstreamCluster.getServiceId());
                temp.put("uris", upstreamCluster.getUris());
                LoadBalanceRsocketRequester loadBalanceRequester = upstreamCluster.getLoadBalanceRequester();
                temp.put("activeUris", loadBalanceRequester.getActiveRSockets().keySet());
                Set<String> unhealthyUris = loadBalanceRequester.getUnhealthyUris();
                if (!unhealthyUris.isEmpty()) {
                    temp.put("unHealthyUris", unhealthyUris);
                }
                temp.put("lastRefreshTimestamp", new Date(loadBalanceRequester.getLastRefreshTimestamp()));
                temp.put("lastHealthCheckTimestamp", new Date(loadBalanceRequester.getLastHealthCheckTimestamp()));
                return temp;
            }).collect(Collectors.toList()));
        }
        //broker upstream
        UpstreamCluster brokerCluster = upstreamClusterManager.getBroker();
        if (brokerCluster != null) {
            info.put("brokers", brokerCluster.getUris());
        }
        if (rsocketServiceProperties.getMetadata() != null && !rsocketServiceProperties.getMetadata().isEmpty()) {
            info.put("metadata", rsocketServiceProperties.getMetadata());
        }
        if (!offlineServices.isEmpty()) {
            info.put("offlineServices", offlineServices);
        }
        return info;
    }

    @WriteOperation
    public Mono<String> operate(String action) {
        if ("online".equalsIgnoreCase(action)) {
            this.serviceStatus = AppStatus.SERVING;
            return updateAppStatus(this.serviceStatus).thenReturn("Succeed to register RSocket services on brokers!");
        } else if (action.startsWith("online-")) {
            String serviceName = action.substring("online-".length());
            ServiceLocator targetService = getServiceLocator(serviceName);
            if (targetService == null) {
                return Mono.just("Service not found:  " + serviceName);
            } else {
                offlineServices.remove(serviceName);
                return sendRegisterService(targetService).thenReturn("Succeed to register " + serviceName + " on brokers!");
            }
        } else if ("offline".equalsIgnoreCase(action)) {
            this.serviceStatus = AppStatus.DOWN;
            return updateAppStatus(this.serviceStatus).thenReturn("Succeed to unregister RSocket services on brokers!");
        } else if (action.startsWith("offline-")) {
            String serviceName = action.substring("offline-".length());
            ServiceLocator targetService = getServiceLocator(serviceName);
            if (targetService == null) {
                return Mono.just("Service not found:  " + serviceName);
            } else {
                offlineServices.add(serviceName);
                return sendUnregisterService(targetService).thenReturn("Succeed to unregister " + serviceName + " on brokers!");
            }
        } else if ("shutdown".equalsIgnoreCase(action)) {
            this.serviceStatus = AppStatus.STOPPED;
            return updateAppStatus(this.serviceStatus)
                    .thenReturn("Succeed to unregister RSocket services on brokers! Please wait almost 60 seconds to shutdown the Spring Boot App!");
        } else if ("refreshUpstreams".equalsIgnoreCase(action)) {
            Collection<UpstreamCluster> allClusters = this.upstreamClusterManager.getAll();
            for (UpstreamCluster upstreamCluster : allClusters) {
                upstreamCluster.refreshUnhealthyUris();
            }
            return Mono.just("Begin to refresh unHealthy upstream clusters now!");
        } else {
            return Mono.just("Unknown action, please use online, offline and shutdown");
        }
    }

    /**
     * 向所有upstream更新app status
     */
    private Mono<Void> updateAppStatus(AppStatus status) {
        return Flux.fromIterable(upstreamClusterManager.getAll())
                .flatMap(upstreamCluster -> upstreamCluster.broadcastCloudEvent(AppStatusEvent.of(RSocketAppContext.ID, status).toCloudEvent()))
                .then();
    }

    /**
     * 向所有upstream注册服务
     */
    private Mono<Void> sendRegisterService(ServiceLocator targetService) {
        CloudEventData<RSocketServicesExposedEvent> cloudEvent = RSocketServicesExposedEvent.of(Collections.singletonList(targetService));
        return Flux.fromIterable(upstreamClusterManager.getAll()).flatMap(upstreamCluster -> upstreamCluster.broadcastCloudEvent(cloudEvent)).then();
    }

    /**
     * 向所有upstream注销服务
     */
    private Mono<Void> sendUnregisterService(ServiceLocator targetService) {
        CloudEventData<RSocketServicesHiddenEvent> cloudEvent = RSocketServicesHiddenEvent.of(Collections.singletonList(targetService));
        return Flux.fromIterable(upstreamClusterManager.getAll()).flatMap(upstreamCluster -> upstreamCluster.broadcastCloudEvent(cloudEvent)).then();
    }

    /**
     * find service locator
     */
    private ServiceLocator getServiceLocator(String serviceName) {
        ServiceLocator targetService = null;
        for (ServiceLocator serviceLocator : RSocketServiceRegistry.exposedServices()) {
            if (serviceName.equals(serviceLocator.getService())) {
                targetService = serviceLocator;
                break;
            }
        }
        return targetService;
    }

    //getter
    public AppStatus getServiceStatus() {
        return serviceStatus;
    }

    public boolean isServiceProvider() {
        return serviceProvider;
    }
}
