package org.kin.rsocket.springcloud.service;

import org.kin.rsocket.core.RSocketAppContext;
import org.kin.rsocket.core.UpstreamCluster;
import org.kin.rsocket.core.event.AppStatusEvent;
import org.kin.rsocket.core.event.CloudEventBuilder;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.PortsUpdateEvent;
import org.kin.rsocket.service.RSocketServiceRequester;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.annotation.Order;

/**
 * 更新broker app端口元数据以及暴露已注册的所有服务
 * <p>
 * spring容器refresh完就执行处理, 使用者可以在{@link ApplicationListener<ContextRefreshedEvent>}实例
 * 或者更高优先级的spring boot事件({@link ApplicationStartedEvent}之前触发的Application事件)处理自定义逻辑
 *
 * @author huangjianqin
 * @date 2021/3/28
 */
@Order(100)
final class RSocketServicesPublisher implements ApplicationListener<ApplicationStartedEvent> {
    private static final Logger log = LoggerFactory.getLogger(RSocketServicesPublisher.class);
    @Autowired
    private RSocketServiceRequester requester;
    @Autowired
    private RSocketServiceProperties serviceConfig;

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        //requester init
        requester.init();

        UpstreamCluster brokerCluster = requester.getBroker();
        if (brokerCluster == null) {
            //没有配置broker可以不用向broker注册暴露的服务
            //本质上就是直连的方式
            log.info("rsocket endpoint to endpoint mode!");
            return;
        }

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

        //4. notify broker app status update
        CloudEventData<AppStatusEvent> appStatusEventCloudEvent = CloudEventBuilder
                .builder(AppStatusEvent.serving(RSocketAppContext.ID))
                .build();

        brokerCluster.broadcastCloudEvent(appStatusEventCloudEvent)
                .doOnSuccess(aVoid -> log.info(String.format("application connected with RSocket Brokers(%s) successfully", String.join(",", serviceConfig.getBrokers()))))
                .subscribe();

        if (serviceConfig.getPort() > 0) {
            //没有绑定rsocket port, 则是无法调用该app 服务, 那么没必要暴露服务
            requester.publishServices();
        }
    }
}