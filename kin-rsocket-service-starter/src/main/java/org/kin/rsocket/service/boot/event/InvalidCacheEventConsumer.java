package org.kin.rsocket.service.boot.event;

import io.cloudevents.CloudEvent;
import org.kin.rsocket.core.event.AbstractCloudEventConsumer;
import org.kin.rsocket.core.event.CacheInvalidEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Objects;

/**
 * 处理{@link CacheInvalidEvent}事件
 * clean spring cache
 *
 * @author huangjianqin
 * @date 2021/3/28
 */
public final class InvalidCacheEventConsumer extends AbstractCloudEventConsumer<CacheInvalidEvent> {
    @Autowired(required = false)
    private CacheManager cacheManager;

    @Override
    public void consume(CloudEvent cloudEvent, CacheInvalidEvent event) {
        if (Objects.isNull(cacheManager)) {
            return;
        }

        event.getKeys().forEach(key -> {
            //cache name:key
            String[] parts = key.split(":", 2);

            Cache cache = cacheManager.getCache(parts[0]);
            if (cache != null) {
                cache.evict(parts[1]);
            }
        });
    }
}