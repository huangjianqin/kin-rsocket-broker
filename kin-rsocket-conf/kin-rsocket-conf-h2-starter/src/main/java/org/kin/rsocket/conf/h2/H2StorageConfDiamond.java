package org.kin.rsocket.conf.h2;

import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.kin.framework.utils.ExceptionUtils;
import org.kin.rsocket.conf.AbstractConfDiamond;
import org.kin.rsocket.conf.ConfDiamond;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PreDestroy;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
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
            return Flux.fromIterable(appMap.keySet()).map(keyName -> group + ConfDiamond.GROUP_KEY_SEPARATOR + keyName);
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
            String[] parts = key.split(ConfDiamond.GROUP_KEY_SEPARATOR, 2);
            if (parts.length == 2) {
                mvStore.openMap(parts[0]).put(parts[1], value);
                mvStore.commit();
                onKvAdd(parts[0], key, value);
            }
        });
    }

    @Override
    public Mono<Void> remove(String key) {
        return Mono.fromRunnable(() -> {
            String[] parts = key.split(ConfDiamond.GROUP_KEY_SEPARATOR, 2);
            if (parts.length == 2) {
                mvStore.openMap(parts[0]).remove(parts[1]);
                mvStore.commit();
                onKvRemoved(parts[0], key);
            }
        });
    }

    @Override
    public Mono<String> get(String key) {
        String[] parts = key.split(ConfDiamond.GROUP_KEY_SEPARATOR, 2);
        if (mvStore.hasMap(parts[0])) {
            MVMap<String, String> appMap = mvStore.openMap(parts[0]);
            if (appMap.containsKey(parts[1])) {
                return Mono.just(appMap.get(parts[1]));
            }
        }
        return Mono.empty();
    }
}
