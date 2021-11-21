package org.kin.rsocket.core.upstream.loadbalance;

import io.netty.buffer.ByteBuf;
import org.kin.framework.utils.CollectionUtils;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author huangjianqin
 * @date 2021/3/27
 */
public class RoundRobinUpstreamLoadBalance implements UpstreamLoadBalance {
    /** 计数器 */
    private final ConcurrentHashMap<Integer, AtomicInteger> counters = new ConcurrentHashMap<>();

    @Override
    public String select(int serviceId, ByteBuf paramBytes, List<String> uris) {
        AtomicInteger counter = counters.computeIfAbsent(serviceId, k -> new AtomicInteger());
        if (CollectionUtils.isEmpty(uris)) {
            return null;
        }
        return uris.get(counter.incrementAndGet() % uris.size());
    }
}
