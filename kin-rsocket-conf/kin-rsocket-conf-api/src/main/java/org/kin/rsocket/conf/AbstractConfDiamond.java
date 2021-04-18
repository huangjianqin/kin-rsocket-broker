package org.kin.rsocket.conf;

import org.kin.framework.collection.Tuple;
import org.kin.framework.utils.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author huangjianqin
 * @date 2021/4/2
 */
public abstract class AbstractConfDiamond implements ConfDiamond {
    /** watcher */
    private final Map<String, Sinks.Many<Tuple<String, String>>> watchNotifications = new ConcurrentHashMap<>();

    @Override
    public final Flux<Tuple<String, String>> watch(String key) {
        if (StringUtils.isBlank(key)) {
            return Flux.error(new IllegalArgumentException("watch key is null"));
        }
        if (!watchNotifications.containsKey(key)) {
            initNotification(key);
        }
        return Flux.create(sink -> watchNotifications.get(key).asFlux().subscribe(sink::next));
    }

    /**
     * 添加配置时触发
     */
    protected final void onKvAdd(String appName, String key, String value) {
        if (watchNotifications.containsKey(key)) {
            watchNotifications.get(key).tryEmitNext(new Tuple<>(key, value));
        }
        if (watchNotifications.containsKey(appName)) {
            watchNotifications.get(appName).tryEmitNext(new Tuple<>(key, value));
        }
    }

    /**
     * 配置移除时触发
     */
    protected final void onKvRemoved(String appName, String key) {
        if (watchNotifications.containsKey(key)) {
            watchNotifications.get(key).tryEmitNext(new Tuple<>(key, ""));
        }
        if (watchNotifications.containsKey(appName)) {
            watchNotifications.get(appName).tryEmitNext(new Tuple<>(key, ""));
        }
    }

    /**
     * 初始化watcher
     */
    private void initNotification(String key) {
        watchNotifications.put(key, Sinks.many().replay().all());
    }
}
