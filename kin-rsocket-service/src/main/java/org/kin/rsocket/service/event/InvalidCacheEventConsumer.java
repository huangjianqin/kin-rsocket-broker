package org.kin.rsocket.service.event;

import org.kin.rsocket.core.event.CacheInvalidEvent;
import org.kin.rsocket.core.event.CloudEventConsumer;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.CloudEventSupport;
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
public class InvalidCacheEventConsumer implements CloudEventConsumer {
    @Autowired(required = false)
    private CacheManager cacheManager;

    @Override
    public boolean shouldAccept(CloudEventData<?> cloudEvent) {
        String type = cloudEvent.getAttributes().getType();
        return cacheManager != null && CacheInvalidEvent.class.getCanonicalName().equalsIgnoreCase(type);
    }

    @Override
    public Mono<Void> consume(CloudEventData<?> cloudEvent) {
        return Mono.fromRunnable(() -> invalidateSpringCache(cloudEvent));
    }

    private void invalidateSpringCache(CloudEventData<?> cloudEvent) {
        CacheInvalidEvent event = CloudEventSupport.unwrapData(cloudEvent, CacheInvalidEvent.class);
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