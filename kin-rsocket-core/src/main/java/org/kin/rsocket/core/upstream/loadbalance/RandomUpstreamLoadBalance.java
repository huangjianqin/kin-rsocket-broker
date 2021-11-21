package org.kin.rsocket.core.upstream.loadbalance;

import io.netty.buffer.ByteBuf;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author huangjianqin
 * @date 2021/11/21
 */
public class RandomUpstreamLoadBalance implements UpstreamLoadBalance {
    @Override
    public String select(int serviceId, ByteBuf paramBytes, List<String> uris) {
        int size = uris.size();
        String selected;
        if (size > 1) {
            selected = uris.get(ThreadLocalRandom.current().nextInt(size));
            if (selected == null) {
                selected = uris.get(0);
            }
        } else {
            selected = uris.get(0);
        }
        return selected;
    }
}