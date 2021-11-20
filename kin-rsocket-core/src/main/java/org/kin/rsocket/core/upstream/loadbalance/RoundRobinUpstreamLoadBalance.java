package org.kin.rsocket.core.upstream.loadbalance;

import io.netty.buffer.ByteBuf;
import org.kin.framework.utils.CollectionUtils;

import java.util.List;

/**
 * @author huangjianqin
 * @date 2021/3/27
 */
public class RoundRobinUpstreamLoadBalance implements UpstreamLoadBalance {
    /** 计数器 */
    private int counter;

    @Override
    public String select(ByteBuf paramBytes, List<String> uris) {
        if (CollectionUtils.isEmpty(uris)) {
            return null;
        }
        return uris.get(counter++ % uris.size());
    }
}
