package org.kin.rsocket.core.upstream.loadbalance;

import io.netty.buffer.ByteBuf;
import io.rsocket.RSocket;
import org.kin.framework.utils.Extension;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author huangjianqin
 * @date 2021/11/21
 */
@Extension("random")
public class RandomUpstreamLoadBalance implements UpstreamLoadBalance {
    @Override
    public RSocket select(int serviceId, ByteBuf paramBytes, List<RSocket> rsockets) {
        int size = rsockets.size();
        RSocket selected;
        if (size > 1) {
            selected = rsockets.get(ThreadLocalRandom.current().nextInt(size));
            if (selected == null) {
                selected = rsockets.get(0);
            }
        } else {
            selected = rsockets.get(0);
        }
        return selected;
    }
}