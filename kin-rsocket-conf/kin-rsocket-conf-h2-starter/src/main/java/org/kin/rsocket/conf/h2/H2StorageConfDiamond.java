package org.kin.rsocket.conf.h2;

import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.kin.framework.utils.PropertiesUtils;
import org.kin.rsocket.conf.AbstractConfDiamond;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PreDestroy;
import java.io.File;
import java.util.Map;
import java.util.Properties;

/**
 * 基于H2 MVStore
 * map即group, 然后是kv对
 *
 * @author huangjianqin
 * @date 2021/4/2
 */
public class H2StorageConfDiamond extends AbstractConfDiamond {
    private static final Logger log = LoggerFactory.getLogger(H2StorageConfDiamond.class);
    /** h2 store */
    private final MVStore mvStore;

    public H2StorageConfDiamond(String dbPath) {
        File dbFile = new File(dbPath);
        if (dbFile.isDirectory() || !dbFile.exists()) {
            throw new IllegalArgumentException("can't load H2 db file");
        }
        mvStore = MVStore.open(dbPath);
        log.info("Success to load Apps configuration from H2 MVStore");
    }

    @PreDestroy
    public void close() {
        mvStore.close();
    }

    @Override
    public Flux<String> getGroups() {
        return Flux.fromIterable(mvStore.getMapNames()).sort();
    }

    @Override
    public Flux<String> findKeysByGroup(String group) {
        MVMap<String, String> appMap = mvStore.openMap(group);
        if (appMap != null && !appMap.isEmpty()) {
            return Flux.fromIterable(appMap.keySet());
        }
        return Flux.empty();
    }

    @Override
    public Mono<String> findKeyValuesByGroup(String group) {
        MVMap<String, String> appMap = mvStore.openMap(group);
        return Mono.just(appMap.entrySet())
                .map(entries -> {
                    Properties properties = new Properties();
                    for (Map.Entry<String, String> entry : entries) {
                        properties.put(entry.getKey(), entry.getValue());
                    }

                    return PropertiesUtils.writePropertiesContent(properties, String.format("app '%s' configs", group));
                }).doOnError(e -> log.error(String.format("conf diamond get all confs from app '%s' error", group), e));
    }

    @Override
    public Mono<Void> put(String group, String key, String value) {
        return Mono.fromRunnable(() -> {
            mvStore.openMap(group).put(key, value);
            mvStore.commit();
            onKvAdd(group, key, value);
        });
    }

    @Override
    public Mono<Void> remove(String group, String key) {
        return Mono.fromRunnable(() -> {
            mvStore.openMap(group).remove(key);
            mvStore.commit();
            onKvRemoved(group, key);
        });
    }

    @Override
    public Mono<String> get(String group, String key) {
        MVMap<String, String> appMap = mvStore.openMap(group);
        if (appMap.containsKey(key)) {
            return Mono.just(appMap.get(key));
        }
        return Mono.empty();
    }
}
