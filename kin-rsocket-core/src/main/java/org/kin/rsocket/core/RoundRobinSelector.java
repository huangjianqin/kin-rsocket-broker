package org.kin.rsocket.core;

import io.rsocket.RSocket;

import java.util.List;

/**
 * @author huangjianqin
 * @date 2021/3/27
 */
public class RoundRobinSelector implements Selector {
    /** 计数器 */
    private int counter;

    @Override
    public RSocket apply(List<RSocket> rsockets) {
        return rsockets.get(counter++ % rsockets.size());
    }
}
