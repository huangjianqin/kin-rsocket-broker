package org.kin.rsocket.service.boot.event;

import org.kin.rsocket.core.event.AbstractCloudEventConsumer;
import org.kin.rsocket.core.event.CacheInvalidEvent;
import org.kin.rsocket.core.event.CloudEventData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import reactor.core.publisher.Mono;

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
    public Mono<Void> consume(CloudEventData<?> cloudEventData, CacheInvalidEvent event) {
        if (Objects.isNull(event)) {
            return Mono.empty();
        }
        return Mono.fromRunnable(() -> invalidateSpringCache(event));
    }

    private void invalidateSpringCache(CacheInvalidEvent event) {
        if (Objects.nonNull(event) && Objects.nonNull(cacheManager)) {
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

}