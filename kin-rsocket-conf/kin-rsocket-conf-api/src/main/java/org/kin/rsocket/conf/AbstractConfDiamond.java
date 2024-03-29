package org.kin.rsocket.conf;

import org.jctools.maps.NonBlockingHashMap;
import org.kin.framework.collection.Tuple;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.core.utils.RetryNonSerializedEmitFailureHandler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

/**
 * @author huangjianqin
 * @date 2021/4/2
 */
public abstract class AbstractConfDiamond implements ConfDiamond {
    /** 配置中心单线程修改数据 */
    protected static final Scheduler SCHEDULER = Schedulers.newSingle("ConfDiamond");

    /** watcher */
    private final Map<String, Sinks.Many<Tuple<String, String>>> watchNotifications = new NonBlockingHashMap<>();

    @Override
    public final Flux<Tuple<String, String>> watch(String group, String key) {
        String storageKey = group;
        if (StringUtils.isNotBlank(key)) {
            storageKey = storageKey + GROUP_KEY_SEPARATOR + key;
        }
        if (StringUtils.isBlank(storageKey)) {
            return Flux.error(new IllegalArgumentException("watch key is null"));
        }
        if (!watchNotifications.containsKey(storageKey)) {
            initNotification(storageKey);
        }

        String finalStorageKey = storageKey;
        return Flux.create(sink -> watchNotifications.get(finalStorageKey).asFlux().subscribe(sink::next));
    }

    @Override
    public Flux<Tuple<String, String>> watch(String group) {
        return watch(group, "");
    }

    /**
     * 添加配置时触发
     */
    protected final void onKvAdd(String group, String key, String value) {
        if (watchNotifications.containsKey(key)) {
            watchNotifications.get(key).emitNext(new Tuple<>(key, value), RetryNonSerializedEmitFailureHandler.RETRY_NON_SERIALIZED);
        }
        if (watchNotifications.containsKey(group)) {
            watchNotifications.get(group).emitNext(new Tuple<>(key, value), RetryNonSerializedEmitFailureHandler.RETRY_NON_SERIALIZED);
        }
    }

    /**
     * 配置移除时触发
     */
    protected final void onKvRemoved(String group, String key) {
        if (watchNotifications.containsKey(key)) {
            watchNotifications.get(key).emitNext(new Tuple<>(key, ""), RetryNonSerializedEmitFailureHandler.RETRY_NON_SERIALIZED);
        }
        if (watchNotifications.containsKey(group)) {
            watchNotifications.get(group).emitNext(new Tuple<>(key, ""), RetryNonSerializedEmitFailureHandler.RETRY_NON_SERIALIZED);
        }
    }

    /**
     * 初始化watcher
     */
    private void initNotification(String key) {
        watchNotifications.put(key, Sinks.many().replay().all());
    }
}
