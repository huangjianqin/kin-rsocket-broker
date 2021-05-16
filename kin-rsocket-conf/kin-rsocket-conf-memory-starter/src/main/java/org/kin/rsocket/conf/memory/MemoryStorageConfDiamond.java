package org.kin.rsocket.conf.memory;

import org.kin.framework.collection.ConcurrentHashSet;
import org.kin.framework.utils.PropertiesUtils;
import org.kin.rsocket.conf.AbstractConfDiamond;
import org.kin.rsocket.conf.ConfDiamond;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author huangjianqin
 * @date 2021/3/29
 */
public class MemoryStorageConfDiamond extends AbstractConfDiamond {
    private static final Logger log = LoggerFactory.getLogger(MemoryStorageConfDiamond.class);

    /** group, 也就是app name */
    private final Set<String> group = new ConcurrentHashSet<>();
    /** key value存储 */
    private final Map<String, String> storage = new ConcurrentHashMap<>();

    @Override
    public Flux<String> getGroups() {
        return Flux.fromIterable(group).sort();
    }

    @Override
    public Flux<String> findKeysByGroup(String group) {
        return Flux.fromIterable(storage.keySet())
                .filter(name -> name.startsWith(group.concat(ConfDiamond.GROUP_KEY_SEPARATOR)))
                .map(name -> name.split(ConfDiamond.GROUP_KEY_SEPARATOR, 2)[1]);
    }

    @Override
    public Mono<String> findKeyValuesByGroup(String group) {
        return Mono.just(storage.entrySet())
                .map(entries -> {
                    Properties properties = new Properties();
                    for (Map.Entry<String, String> entry : entries) {
                        String key = entry.getKey();
                        if (!key.startsWith(group.concat(ConfDiamond.GROUP_KEY_SEPARATOR))) {
                            continue;
                        }
                        properties.put(key.split(ConfDiamond.GROUP_KEY_SEPARATOR, 2)[1], entry.getValue());
                    }

                    return PropertiesUtils.writePropertiesContent(properties, String.format("app '%s' configs", group));
                }).doOnError(e -> log.error(String.format("conf diamond get all confs from app '%s' error", group), e));
    }

    @Override
    public Mono<Void> put(String group, String key, String value) {
        return Mono.<Void>fromRunnable(() -> {
            storage.put(group + GROUP_KEY_SEPARATOR + key, value);
            this.group.add(group);
            onKvAdd(group, key, value);
        }).publishOn(SCHEDULER);
    }

    @Override
    public Mono<Void> remove(String group, String key) {
        return Mono.<Void>fromRunnable(() -> {
            storage.remove(group + GROUP_KEY_SEPARATOR + key);
            this.group.remove(group);
            super.onKvRemoved(group, key);
        }).publishOn(SCHEDULER);
    }

    @Override
    public Mono<String> get(String group, String key) {
        String storageKey = group + GROUP_KEY_SEPARATOR + key;
        if (storage.containsKey(storageKey)) {
            return Mono.just(storage.get(storageKey));
        }
        return Mono.empty();
    }
}
