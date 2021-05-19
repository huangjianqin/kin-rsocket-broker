package org.kin.rsocket.broker.event;

import org.kin.framework.Closeable;
import org.kin.framework.utils.PropertiesUtils;
import org.kin.rsocket.broker.BrokerResponder;
import org.kin.rsocket.broker.RSocketServiceManager;
import org.kin.rsocket.conf.ConfDiamond;
import org.kin.rsocket.core.domain.AppStatus;
import org.kin.rsocket.core.event.*;
import org.kin.rsocket.core.metadata.AppMetadata;
import org.kin.rsocket.core.utils.UriUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @author huangjianqin
 * @date 2021/3/30
 */
public final class AppStatusEventConsumer extends AbstractCloudEventConsumer<AppStatusEvent> implements Closeable, DisposableBean {
    @Autowired
    private RSocketServiceManager serviceManager;
    @Autowired
    private ConfDiamond confDiamond;
    /**
     * broker配置中心watch listener
     */
    private Map<String, Disposable> listeners = new HashMap<>();

    @Override
    public Mono<Void> consume(CloudEventData<?> cloudEventData, AppStatusEvent event) {
        //安全验证，确保appStatusEvent的ID和cloud source来源的id一致
        if (event != null && event.getId().equals(UriUtils.getAppUUID(cloudEventData.getAttributes().getSource()))) {
            BrokerResponder responder = serviceManager.getByUUID(event.getId());
            if (responder != null) {
                AppMetadata appMetadata = responder.getAppMetadata();
                if (event.getStatus().equals(AppStatus.CONNECTED)) {
                    //app connected
                    String autoRefreshKey = "auto-refresh";
                    if ("true".equalsIgnoreCase(appMetadata.getMetadata(autoRefreshKey))) {
                        //broker主动监听配置变化, 并通知app refresh context
                        listenConfChange(appMetadata);
                    }
                } else if (event.getStatus().equals(AppStatus.SERVING)) {
                    //app serving
                    responder.publishServices();
                } else if (event.getStatus().equals(AppStatus.DOWN)) {
                    //app out of service
                    responder.hideServices();
                } else if (event.getStatus().equals(AppStatus.STOPPED)) {
                    //app stopped
                    responder.hideServices();
                    responder.setAppStatus(AppStatus.STOPPED);
                }
            }
        }
        return Mono.empty();
    }

    /**
     * 注册conf配置变化监听
     */
    private void listenConfChange(AppMetadata appMetadata) {
        String appName = appMetadata.getName();
        if (!listeners.containsKey(appName)) {
            listeners.put(appName, confDiamond.watch(appName).subscribe(config -> {
                Properties properties = new Properties();
                properties.put(config.first(), config.second());

                String propertiesContent = PropertiesUtils.writePropertiesContent(properties);

                CloudEventData<ConfigChangedEvent> configChangedEvent =
                        CloudEventBuilder.builder(ConfigChangedEvent.of(appName, propertiesContent)).build();
                serviceManager.broadcast(appName, configChangedEvent).subscribe();
            }));
        }
    }

    @Override
    public void destroy() {
        close();
    }

    @Override
    public void close() {
        for (Disposable disposable : listeners.values()) {
            disposable.dispose();
        }
    }
}
