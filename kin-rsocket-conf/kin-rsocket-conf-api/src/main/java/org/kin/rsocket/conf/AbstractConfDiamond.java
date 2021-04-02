package org.kin.rsocket.conf;

import org.kin.framework.collection.Tuple;
import reactor.core.publisher.Flux;
import reactor.core.publisher.ReplayProcessor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author huangjianqin
 * @date 2021/4/2
 */
public abstract class AbstractConfDiamond implements ConfDiamond {
    /** watcher */
    private final Map<String, ReplayProcessor<Tuple<String, String>>> watchNotifications = new ConcurrentHashMap<>();

    @Override
    public final Flux<Tuple<String, String>> watch(String key) {
        if (!watchNotifications.containsKey(key)) {
            initNotification(key);
        }
        return Flux.create(sink -> watchNotifications.get(key).subscribe(sink::next));
    }

    /**
     * 添加配置时触发
     */
    protected final void onKvAdd(String key, String value) {
        if (!watchNotifications.containsKey(key)) {
            initNotification(key);
        }
        watchNotifications.get(key).onNext(new Tuple<>(key, value));
    }

    /**
     * 配置移除时触发
     */
    protected final void onKvRemoved(String key) {
        if (watchNotifications.containsKey(key)) {
            watchNotifications.get(key).onNext(new Tuple<>(key, ""));
        }
    }

    /**
     * 初始化watcher
     */
    private void initNotification(String key) {
        watchNotifications.put(key, ReplayProcessor.cacheLast());
    }
}
