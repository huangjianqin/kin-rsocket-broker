package org.kin.rsocket.core.upstream.loadbalance;

import io.netty.buffer.ByteBuf;
import io.rsocket.RSocket;
import org.jctools.maps.NonBlockingHashMap;
import org.kin.framework.utils.CollectionUtils;
import org.kin.framework.utils.Extension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author huangjianqin
 * @date 2021/3/27
 */
@Extension("roundRobin")
public class RoundRobinUpstreamLoadBalance implements UpstreamLoadBalance {
    /** 计数器 */
    private final Map<Integer, AtomicInteger> counters = new NonBlockingHashMap<>();

    @Override
    public RSocket select(int serviceId, ByteBuf paramBytes, List<RSocket> uris) {
        AtomicInteger counter = counters.computeIfAbsent(serviceId, k -> new AtomicInteger());
        if (CollectionUtils.isEmpty(uris)) {
            return null;
        }
        return uris.get(counter.incrementAndGet() % uris.size());
    }
}
