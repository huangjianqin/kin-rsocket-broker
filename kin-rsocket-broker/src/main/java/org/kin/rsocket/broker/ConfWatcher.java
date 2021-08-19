package org.kin.rsocket.broker;

import org.kin.framework.Closeable;
import org.kin.framework.utils.PropertiesUtils;
import org.kin.rsocket.conf.ConfDiamond;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.ConfigChangedEvent;
import org.kin.rsocket.core.metadata.AppMetadata;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.Disposable;

import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 负责管理自动刷新配置监听
 *
 * @author huangjianqin
 * @date 2021/8/19
 */
public final class ConfWatcher implements Closeable, DisposableBean {
    @Autowired
    private ConfDiamond confDiamond;
    @Autowired
    private RSocketServiceManager serviceManager;

    /** broker配置中心watch listener */
    private final Map<String, Disposable> listeners = new ConcurrentHashMap<>();

    /**
     * 注册conf配置变化监听
     */
    public void listenConfChange(AppMetadata appMetadata) {
        String appName = appMetadata.getName();
        if (!listeners.containsKey(appName)) {
            listeners.put(appName, confDiamond.watch(appName).subscribe(config -> {
                Properties properties = new Properties();
                properties.put(config.first(), config.second());

                String propertiesContent = PropertiesUtils.writePropertiesContent(properties);

                CloudEventData<ConfigChangedEvent> configChangedEvent = ConfigChangedEvent.of(appName, propertiesContent).toCloudEvent();
                serviceManager.broadcast(appName, configChangedEvent).subscribe();
            }));
        }
    }

    /**
     * 尝试移除无用watch
     */
    public void tryRemoveInvalidListen() {
        Set<String> validAppNames = serviceManager.getAllAppNames();
        for (String appName : listeners.keySet()) {
            if (validAppNames.contains(appName)) {
                continue;
            }

            Disposable removed = listeners.remove(appName);
            removed.dispose();
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
