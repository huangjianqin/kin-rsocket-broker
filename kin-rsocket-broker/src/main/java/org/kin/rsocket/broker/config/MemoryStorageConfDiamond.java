package org.kin.rsocket.broker.config;

import org.kin.framework.collection.ConcurrentHashSet;
import org.kin.framework.collection.Tuple;
import org.kin.rsocket.core.RSocketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.ReplayProcessor;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author huangjianqin
 * @date 2021/3/29
 */
@RSocketService(serviceInterface = ConfDiamond.class)
public class MemoryStorageConfDiamond implements ConfDiamond {
    private static Logger log = LoggerFactory.getLogger(MemoryStorageConfDiamond.class);
    /** group和key分隔符 */
    private static final String GROUP_KEY_SEPARATOR = ":";

    /** app name, 也就是group */
    private final Set<String> appNames = new ConcurrentHashSet<>();
    /** key value存储 */
    private final Map<String, String> storage = new ConcurrentHashMap<>();
    /** watcher */
    private final Map<String, ReplayProcessor<Tuple<String, String>>> watchNotifications = new ConcurrentHashMap<>();

    @Override
    public Flux<String> getGroups() {
        return Flux.fromIterable(appNames).sort();
    }

    @Override
    public Flux<String> findKeysByGroup(String group) {
        return Flux.fromIterable(storage.keySet())
                .filter(name -> name.startsWith(group.concat(GROUP_KEY_SEPARATOR)));
    }

    @Override
    public Mono<Void> put(String key, String value) {
        return Mono.fromRunnable(() -> {
            storage.put(key, value);
            if (key.contains(GROUP_KEY_SEPARATOR)) {
                appNames.add(key.substring(0, key.indexOf(GROUP_KEY_SEPARATOR)));
            }
            if (!watchNotifications.containsKey(key)) {
                initNotification(key);
            }
            watchNotifications.get(key).onNext(new Tuple<>(key, value));
        });
    }

    @Override
    public Mono<Void> remove(String key) {
        return Mono.fromRunnable(() -> {
            storage.remove(key);
            if (watchNotifications.containsKey(key)) {
                watchNotifications.get(key).onNext(new Tuple<>(key, ""));
            }
        });
    }

    @Override
    public Mono<String> get(String key) {
        if (storage.containsKey(key)) {
            return Mono.just(storage.get(key));
        }
        return Mono.empty();
    }

    @Override
    public Flux<Tuple<String, String>> watch(String key) {
        if (!watchNotifications.containsKey(key)) {
            initNotification(key);
        }
        return Flux.create(sink -> watchNotifications.get(key).subscribe(sink::next));
    }

    /**
     * 初始化watcher
     */
    private void initNotification(String key) {
        watchNotifications.put(key, ReplayProcessor.cacheLast());
    }
}
