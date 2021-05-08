package org.kin.rsocket.springcloud.service;

import org.kin.rsocket.core.RSocketAppContext;
import org.kin.rsocket.core.RSocketBinderBuilderCustomizer;
import org.kin.rsocket.core.UpstreamCluster;
import org.kin.rsocket.core.event.CloudEventBuilder;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.PortsUpdateEvent;
import org.kin.rsocket.service.RSocketServiceConnector;
import org.kin.rsocket.service.RequesterSupportBuilderCustomizer;
import org.kin.rsocket.springcloud.service.health.HealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.annotation.Order;

import java.util.stream.Collectors;

/**
 * 1. connection构建
 * 2. send cloud event to broker, 其中包括服务暴露事件
 * <p>
 * spring容器refresh完就执行处理, 使用者可以在更低优先级的{@link ApplicationListener<ContextRefreshedEvent>}实例
 * 或者spring boot事件({@link ApplicationStartedEvent}之后触发的事件)处理自定义逻辑
 *
 * @author huangjianqin
 * @date 2021/3/28
 * @see ContextRefreshedEvent
 */
@Order(-100)
final class ServicesPublisher implements ApplicationListener<ContextRefreshedEvent> {
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
    public void onApplicationEvent(ContextRefreshedEvent event) {
        //connect
        connector.connect(
                binderBuilderCustomizers.orderedStream().collect(Collectors.toList()),
                requesterSupportBuilderCustomizers.orderedStream().collect(Collectors.toList()),
                healthService);

        UpstreamCluster brokerCluster = connector.getBroker();
        if (brokerCluster == null) {
            //没有配置broker可以不用向broker注册暴露的服务
            //本质上就是直连的方式
            log.info("rsocket endpoint to endpoint mode!");
            return;
        }

        //broker uris
        String brokerUris = String.join(",", brokerCluster.getUris());

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
            brokerCluster.broadcastCloudEvent(portsUpdateCloudEvent).subscribe();
        }

        connector.publishServices();
    }
}