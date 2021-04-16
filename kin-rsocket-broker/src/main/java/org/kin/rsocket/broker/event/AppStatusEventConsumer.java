package org.kin.rsocket.broker.event;

import org.kin.framework.Closeable;
import org.kin.framework.utils.ExceptionUtils;
import org.kin.rsocket.broker.ServiceManager;
import org.kin.rsocket.broker.ServiceResponder;
import org.kin.rsocket.conf.ConfDiamond;
import org.kin.rsocket.core.domain.AppStatus;
import org.kin.rsocket.core.event.*;
import org.kin.rsocket.core.metadata.AppMetadata;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @author huangjianqin
 * @date 2021/3/30
 */
public final class AppStatusEventConsumer implements CloudEventConsumer, Closeable, DisposableBean {
    @Autowired
    private ServiceManager serviceManager;
    @Autowired
    private ConfDiamond confDiamond;
    /**
     *
     */
    private Map<String, Disposable> listeners = new HashMap<>();

    @Override
    public boolean shouldAccept(CloudEventData<?> cloudEvent) {
        return AppStatusEvent.class.getCanonicalName().equalsIgnoreCase(cloudEvent.getAttributes().getType());
    }

    @Override
    public Mono<Void> consume(CloudEventData<?> cloudEvent) {
        AppStatusEvent event = CloudEventSupport.unwrapData(cloudEvent, AppStatusEvent.class);
        //安全验证，确保appStatusEvent的ID和cloud source来源的id一致
        if (event != null && event.getId().equals(cloudEvent.getAttributes().getSource().getHost())) {
            ServiceResponder responder = serviceManager.getByUUID(event.getId());
            if (responder != null) {
                AppMetadata appMetadata = responder.getAppMetadata();
                if (event.getStatus().equals(AppStatus.CONNECTED)) {  //app connected
                    listenConfChange(appMetadata);
                } else if (event.getStatus().equals(AppStatus.SERVING)) {  //app serving
                    responder.registerPublishedServices();
                } else if (event.getStatus().equals(AppStatus.DOWN)) { //app out of service
                    responder.unregisterPublishedServices();
                } else if (event.getStatus().equals(AppStatus.STOPPED)) {
                    responder.unregisterPublishedServices();
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

                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                properties.list(pw);

                pw.close();
                try {
                    sw.close();
                } catch (IOException e) {
                    ExceptionUtils.throwExt(e);
                }

                CloudEventData<ConfigChangedEvent> configChangedEvent =
                        CloudEventBuilder.builder(ConfigChangedEvent.of(appName, sw.toString())).build();
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
