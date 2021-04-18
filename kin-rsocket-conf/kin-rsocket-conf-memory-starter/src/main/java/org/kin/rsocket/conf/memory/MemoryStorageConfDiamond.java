package org.kin.rsocket.conf.memory;

import org.kin.framework.collection.ConcurrentHashSet;
import org.kin.framework.utils.ExceptionUtils;
import org.kin.rsocket.conf.AbstractConfDiamond;
import org.kin.rsocket.conf.ConfDiamond;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.Properties;
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
    public Mono<String> findKeyValuesByGroup(String group) {
        return Mono.just(storage.entrySet())
                .map(entries -> {
                    Properties properties = new Properties();
                    for (Map.Entry<String, String> entry : entries) {
                        if (!entry.getKey().startsWith(group.concat(ConfDiamond.GROUP_KEY_SEPARATOR))) {
                            continue;
                        }
                        properties.put(entry.getKey(), entry.getValue());
                    }

                    try {
                        StringWriter sw = new StringWriter();
                        PrintWriter pw = new PrintWriter(sw);
                        properties.list(pw);

                        pw.close();
                        sw.close();
                        return sw.toString();
                    } catch (Exception e) {
                        ExceptionUtils.throwExt(e);
                    }

                    return "";
                }).doOnError(e -> log.error(String.format("conf diamond get all confs from app '%s' error", group), e));
    }

    @Override
    public Mono<Void> put(String key, String value) {
        return Mono.fromRunnable(() -> {
            storage.put(key, value);
            String appName = "";
            if (key.contains(ConfDiamond.GROUP_KEY_SEPARATOR)) {
                appName = key.substring(0, key.indexOf(ConfDiamond.GROUP_KEY_SEPARATOR));
                appNames.add(appName);
            }
            onKvAdd(appName, key, value);
        });
    }

    @Override
    public Mono<Void> remove(String key) {
        return Mono.fromRunnable(() -> {
            storage.remove(key);
            String appName = "";
            if (key.contains(ConfDiamond.GROUP_KEY_SEPARATOR)) {
                appName = key.substring(0, key.indexOf(ConfDiamond.GROUP_KEY_SEPARATOR));
            }
            super.onKvRemoved(appName, key);
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
