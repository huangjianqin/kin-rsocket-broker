package org.kin.rsocket.conf.memory;

import org.kin.framework.collection.ConcurrentHashSet;
import org.kin.rsocket.conf.AbstractConfDiamond;
import org.kin.rsocket.conf.ConfDiamond;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author huangjianqin
 * @date 2021/3/29
 */
public class MemoryStorageConfDiamond extends AbstractConfDiamond {
    private static Logger log = LoggerFactory.getLogger(MemoryStorageConfDiamond.class);

    /** app name, 也就是group */
    private final Set<String> appNames = new ConcurrentHashSet<>();
    /** key value存储 */
    private final Map<String, String> storage = new ConcurrentHashMap<>();

    @Override
    public Flux<String> getGroups() {
        return Flux.fromIterable(appNames).sort();
    }

    @Override
    public Flux<String> findKeysByGroup(String group) {
        return Flux.fromIterable(storage.keySet())
                .filter(name -> name.startsWith(group.concat(ConfDiamond.GROUP_KEY_SEPARATOR)));
    }

    @Override
    public Mono<Void> put(String key, String value) {
        return Mono.fromRunnable(() -> {
            storage.put(key, value);
            if (key.contains(ConfDiamond.GROUP_KEY_SEPARATOR)) {
                appNames.add(key.substring(0, key.indexOf(ConfDiamond.GROUP_KEY_SEPARATOR)));
            }
            onKvAdd(key, value);
        });
    }

    @Override
    public Mono<Void> remove(String key) {
        return Mono.fromRunnable(() -> {
            storage.remove(key);
            super.onKvRemoved(key);
        });
    }

    @Override
    public Mono<String> get(String key) {
        if (storage.containsKey(key)) {
            return Mono.just(storage.get(key));
        }
        return Mono.empty();
    }
}
