package org.kin.rsocket.broker.event;

import org.kin.rsocket.broker.ConfWatcher;
import org.kin.rsocket.broker.RSocketEndpoint;
import org.kin.rsocket.broker.RSocketServiceManager;
import org.kin.rsocket.core.domain.AppStatus;
import org.kin.rsocket.core.event.AbstractCloudEventConsumer;
import org.kin.rsocket.core.event.AppStatusEvent;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.metadata.AppMetadata;
import org.kin.rsocket.core.utils.UriUtils;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * @author huangjianqin
 * @date 2021/3/30
 */
public final class AppStatusEventConsumer extends AbstractCloudEventConsumer<AppStatusEvent> {
    @Autowired
    private RSocketServiceManager serviceManager;
    @Autowired(required = false)
    private ConfWatcher confWatcher;

    @Override
    public Mono<Void> consume(CloudEventData<?> cloudEventData, AppStatusEvent event) {
        //安全验证，确保appStatusEvent的ID和cloud source来源的id一致
        if (event != null) {
            String appId = event.getId();
            if (appId.equals(UriUtils.getAppUUID(cloudEventData.getAttributes().getSource()))) {
                RSocketEndpoint rsocketEndpoint = serviceManager.getByUUID(appId);
                if (Objects.nonNull(rsocketEndpoint)) {
                    AppMetadata appMetadata = rsocketEndpoint.getAppMetadata();
                    if (event.getStatus().equals(AppStatus.CONNECTED)) {
                        //app connected
                        if (Objects.nonNull(confWatcher)) {
                            String autoRefreshKey = "auto-refresh";
                            if ("true".equalsIgnoreCase(appMetadata.getMetadata(autoRefreshKey))) {
                                //broker主动监听配置变化, 并通知app refresh context
                                confWatcher.listenConfChange(appMetadata);
                            }
                        }
                    } else if (event.getStatus().equals(AppStatus.SERVING)) {
                        //app serving
                        rsocketEndpoint.publishServices();
                    } else if (event.getStatus().equals(AppStatus.DOWN)) {
                        //app out of service
                        rsocketEndpoint.hideServices();
                    } else if (event.getStatus().equals(AppStatus.STOPPED)) {
                        //app stopped
                        rsocketEndpoint.hideServices();
                        rsocketEndpoint.setAppStatus(AppStatus.STOPPED);
                        if (Objects.nonNull(confWatcher)) {
                            confWatcher.tryRemoveInvalidListen();
                        }
                    }
                } else {
                    //upstream断开连接时, 先移除responder, 再广播事件, 所以此次要特殊处理一下
                    if (event.getStatus().equals(AppStatus.STOPPED)) {
                        //app stopped
                        if (Objects.nonNull(confWatcher)) {
                            confWatcher.tryRemoveInvalidListen();
                        }
                    }
                }
            }
        }
        return Mono.empty();
    }
}
