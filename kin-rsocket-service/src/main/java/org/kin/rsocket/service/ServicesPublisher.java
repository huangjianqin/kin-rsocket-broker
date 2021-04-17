package org.kin.rsocket.service;

import org.kin.rsocket.core.RSocketAppContext;
import org.kin.rsocket.core.RSocketBinderBuilderCustomizer;
import org.kin.rsocket.core.UpstreamCluster;
import org.kin.rsocket.core.event.CloudEventBuilder;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.PortsUpdateEvent;
import org.kin.rsocket.service.health.HealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationStartedEvent;
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
final class ServicesPublisher implements ApplicationListener<ApplicationStartedEvent> {
    private static final Logger log = LoggerFactory.getLogger(ServicesPublisher.class);
    @Autowired
    private RSocketServiceConnector connector;
    @Autowired
    private ObjectProvider<RSocketBinderBuilderCustomizer> binderBuilderCustomizers;
    @Autowired
    private ObjectProvider<RequesterSupportBuilderCustomizer> requesterSupportBuilderCustomizers;
    @Autowired
    private HealthService healthService;

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        //connect
        connector.connect(
                binderBuilderCustomizers.orderedStream().collect(Collectors.toList()),
                requesterSupportBuilderCustomizers.orderedStream().collect(Collectors.toList()),
                healthService);

        UpstreamCluster brokerCluster = connector.getUpstreamClusterManager().getBroker();
        if (brokerCluster == null) {
            //没有配置broker可以不用向broker注册暴露的服务
            //本质上就是直连的方式
            log.info("rsocket endpoint to endpoint mode!");
            return;
        }

        //broker uris
        String brokerUris = String.join(",", brokerCluster.getUris());

        ConfigurableEnvironment env = event.getApplicationContext().getEnvironment();
        int serverPort = Integer.parseInt(env.getProperty("server.port", "0"));
        if (serverPort == 0) {
            //ports update
            if (RSocketAppContext.webPort > 0 || RSocketAppContext.managementPort > 0 || RSocketAppContext.rsocketPorts != null) {
                PortsUpdateEvent portsUpdateEvent = new PortsUpdateEvent();
                portsUpdateEvent.setAppId(RSocketAppContext.ID);
                portsUpdateEvent.setWebPort(RSocketAppContext.webPort);
                portsUpdateEvent.setManagementPort(RSocketAppContext.managementPort);
                //todo 验证此时RSocketAppContext.rsocketPorts是否已赋值
                portsUpdateEvent.setRsocketPorts(RSocketAppContext.rsocketPorts);
                CloudEventData<PortsUpdateEvent> portsUpdateCloudEvent = CloudEventBuilder
                        .builder(portsUpdateEvent)
                        .build();
                brokerCluster.broadcastCloudEvent(portsUpdateCloudEvent)
                        .doOnSuccess(aVoid -> log.info(String.format("Application connected with RSocket Brokers(%s) successfully", brokerUris)))
                        .subscribe();
            }
        }

        connector.publishServices();
    }
}