package org.kin.rsocket.core.upstream.loadbalance;

import io.netty.buffer.ByteBuf;
import io.rsocket.RSocket;
import org.kin.framework.utils.SPI;

import java.util.List;

/**
 * requester端
 * 上游服务(broker|p2p service)负载均衡策略
 *
 * @author huangjianqin
 * @date 2021/3/27
 */
@SPI(alias = "upstreamLoadBalance", value = "roundRobin")
@FunctionalInterface
public interface UpstreamLoadBalance {
    /**
     * 选择一个合适的upstream rsocket
     *
     * @param paramBytes 请求的参数序列化后bytes
     * @param rsockets   upstream rsocket
     */
    RSocket select(int serviceId, ByteBuf paramBytes, List<RSocket> rsockets);
}
