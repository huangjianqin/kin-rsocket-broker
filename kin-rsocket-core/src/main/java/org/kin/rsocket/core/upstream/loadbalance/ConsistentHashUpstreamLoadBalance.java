package org.kin.rsocket.core.upstream.loadbalance;

import io.netty.buffer.ByteBuf;
import org.kin.framework.utils.CollectionUtils;

import java.util.List;
import java.util.Objects;

/**
 * @author huangjianqin
 * @date 2021/11/20
 */
public class ConsistentHashUpstreamLoadBalance extends RoundRobinUpstreamLoadBalance {
    private volatile ConsistentHash consistentHash;

    @Override
    public String select(ByteBuf paramBytes, List<String> uris) {
        if (Objects.isNull(paramBytes)) {
            //requestChannel的时候paramBytes为null, 故回退到使用round robin模式
            return super.select(null, uris);
        }
        if (CollectionUtils.isEmpty(uris)) {
            return null;
        }

        int hashCode = uris.hashCode();
        if (consistentHash == null || consistentHash.hashCode != hashCode) {
            consistentHash = new ConsistentHash(uris, hashCode);
        }

        byte[] bytes = new byte[paramBytes.readableBytes()];
        paramBytes.markReaderIndex();
        paramBytes.readBytes(bytes);
        String target = consistentHash.get(bytes);
        paramBytes.resetReaderIndex();

        return target;
    }

    /**
     * 不可变hash环
     * 如果发现upstream rsocket uris发生变化时, 直接替换
     */
    private static class ConsistentHash extends org.kin.framework.utils.ConsistentHash<String> {
        /** hash环每个节点数量(含虚拟节点) */
        private static final int HASH_NODE_NUM = 128;
        /** 标识该hash环对应的upstream rsocket uris, 用于判断upstream rsocket uris是否发生变化 */
        private int hashCode;

        public ConsistentHash(List<String> uris, int hashCode) {
            super(HASH_NODE_NUM);

            for (String uri : uris) {
                add(uri);
            }
            this.hashCode = hashCode;
        }

        @Override
        public void add(String obj, int weight) {
            if (hashCode == 0) {
                //初始化
                super.add(obj, weight);
                return;
            }
            throw new UnsupportedOperationException();
        }

        @Override
        public void remove(String obj, int weight) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void add(String obj) {
            if (hashCode == 0) {
                //初始化
                super.add(obj);
                return;
            }
            throw new UnsupportedOperationException();
        }

        @Override
        public void remove(String obj) {
            throw new UnsupportedOperationException();
        }
    }

}
