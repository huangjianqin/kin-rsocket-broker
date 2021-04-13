package org.kin.rsocket.service;

import org.kin.rsocket.core.RSocketAppContext;
import org.kin.rsocket.core.RequesterSupport;
import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.UpstreamCluster;
import org.kin.rsocket.core.event.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.stream.Collectors;

/**
 * 服务暴露给broker
 * todo application ready 时机是否合适
 *
 * @author huangjianqin
 * @date 2021/3/28
 */
public class ServicesPublisher implements ApplicationListener<ApplicationReadyEvent> {
    private static final Logger log = LoggerFactory.getLogger(ServicesPublisher.class);
    @Autowired
    private UpstreamClusterManager upstreamClusterManager;
    @Autowired
    private RequesterSupport requesterSupport;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent applicationReadyEvent) {
        UpstreamCluster brokerCluster = upstreamClusterManager.getBroker();
        if (brokerCluster == null) {
            //没有配置broker可以不用向broker注册暴露的服务
            //本质上就是直连的方式
            log.info("rsocket endpoint to endpoint mode!");
            return;
        }

        //rsocket broker cluster logic
        CloudEventData<AppStatusEvent> appStatusEventCloudEvent = CloudEventBuilder
                .builder(AppStatusEvent.serving(RSocketAppContext.ID))
                .build();

        //broker uris
        String brokerUris = String.join(",", brokerCluster.getUris());

        ConfigurableEnvironment env = applicationReadyEvent.getApplicationContext().getEnvironment();
        int serverPort = Integer.parseInt(env.getProperty("server.port", "0"));
        if (serverPort == 0) {
            //ports update
            if (RSocketAppContext.webPort > 0 || RSocketAppContext.managementPort > 0 || RSocketAppContext.rsocketPorts != null) {
                PortsUpdateEvent portsUpdateEvent = new PortsUpdateEvent();
                portsUpdateEvent.setAppId(RSocketAppContext.ID);
                portsUpdateEvent.setWebPort(RSocketAppContext.webPort);
                portsUpdateEvent.setManagementPort(RSocketAppContext.managementPort);
                portsUpdateEvent.setRsocketPorts(RSocketAppContext.rsocketPorts);
                CloudEventData<PortsUpdateEvent> portsUpdateCloudEvent = CloudEventBuilder
                        .builder(portsUpdateEvent)
                        .build();
                brokerCluster.broadcastCloudEvent(portsUpdateCloudEvent)
                        .doOnSuccess(aVoid -> log.info(String.format("Application connected with RSocket Brokers(%s) successfully", brokerUris)))
                        .subscribe();
            }
        }

        // app status
        brokerCluster.broadcastCloudEvent(appStatusEventCloudEvent)
                .doOnSuccess(aVoid -> log.info(String.format("Application connected with RSocket Brokers(%s) successfully", brokerUris)))
                .subscribe();

        // service exposed
        CloudEventData<ServicesExposedEvent> servicesExposedEventCloudEvent = requesterSupport.servicesExposedEvent().get();
        if (servicesExposedEventCloudEvent != null) {
            brokerCluster.broadcastCloudEvent(servicesExposedEventCloudEvent)
                    .doOnSuccess(aVoid -> {
                        String exposedServiceGsvs = requesterSupport.exposedServices().get().stream().map(ServiceLocator::getGsv).collect(Collectors.joining(","));
                        log.info(String.format("Services(%s) published on Brokers(%s)!.", exposedServiceGsvs, brokerUris));
                    }).subscribe();
        }
    }
}