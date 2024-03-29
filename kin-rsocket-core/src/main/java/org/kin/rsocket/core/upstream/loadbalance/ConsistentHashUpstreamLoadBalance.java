package org.kin.rsocket.core.upstream.loadbalance;

import io.netty.buffer.ByteBuf;
import io.rsocket.RSocket;
import org.jctools.maps.NonBlockingHashMap;
import org.kin.framework.utils.CollectionUtils;
import org.kin.framework.utils.Extension;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;

/**
 * @author huangjianqin
 * @date 2021/11/20
 */
@Extension("consistentHash")
public class ConsistentHashUpstreamLoadBalance extends RoundRobinUpstreamLoadBalance {
    private final Map<Integer, ConsistentHash> consistentHashMap = new NonBlockingHashMap<>();

    @Override
    public RSocket select(int serviceId, ByteBuf paramBytes, List<RSocket> rsockets) {
        if (Objects.isNull(paramBytes)) {
            //requestChannel的时候paramBytes为null, 故回退到使用round robin模式
            return super.select(serviceId, null, rsockets);
        }

        if (CollectionUtils.isEmpty(rsockets)) {
            return null;
        }

        int hashCode = rsockets.hashCode();
        ConsistentHash consistentHash = consistentHashMap.computeIfAbsent(serviceId, k -> new ConsistentHash(rsockets, hashCode));
        if (consistentHash.hashCode != hashCode) {
            consistentHash = consistentHashMap.computeIfPresent(serviceId, (k, v) -> new ConsistentHash(rsockets, hashCode));
        }

        if (Objects.isNull(consistentHash)) {
            //兜底, 理论上不会到这里
            return rsockets.get(0);
        }

        byte[] bytes = new byte[paramBytes.readableBytes()];
        paramBytes.markReaderIndex();
        paramBytes.readBytes(bytes);
        RSocket target = consistentHash.get(bytes);
        paramBytes.resetReaderIndex();

        return target;
    }

    /**
     * 不可变hash环
     * 如果发现upstream rsocket发生变化时, 直接替换
     */
    private static class ConsistentHash extends org.kin.framework.utils.ConsistentHash<RSocket> {
        /** hash环每个节点数量(含虚拟节点) */
        private static final int HASH_NODE_NUM = 128;
        /** 标识该hash环对应的upstream rsocket, 用于判断upstream rsocket是否发生变化 */
        private final int hashCode;

        public ConsistentHash(List<RSocket> rsockets, int hashCode) {
            super(HASH_NODE_NUM);

            for (RSocket rsocket : rsockets) {
                add(rsocket);
            }
            this.hashCode = hashCode;
        }

        @Override
        protected void applySlot(SortedMap<Long, RSocket> circle, String s, RSocket node) {
            if (hashCode == 0) {
                //初始化
                super.applySlot(circle, s, node);
                return;
            }

            throw new UnsupportedOperationException();
        }

        @Override
        protected void removeSlot(SortedMap<Long, RSocket> circle, String s) {
            throw new UnsupportedOperationException();
        }
    }

}
