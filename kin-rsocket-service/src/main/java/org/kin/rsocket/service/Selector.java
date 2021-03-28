package org.kin.rsocket.service;

import io.rsocket.RSocket;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * @author huangjianqin
 * @date 2021/3/27
 */
@FunctionalInterface
public interface Selector extends Function<List<RSocket>, RSocket> {
    /** 套壳 */
    default RSocket select(List<RSocket> list) {
        return apply(list);
    }

    /** 随机 */
    Selector RANDOM = (list) -> {
        int size = list.size();
        RSocket selected;
        if (size > 1) {
            selected = list.get(ThreadLocalRandom.current().nextInt(size));
            if (selected == null) {
                selected = list.get(0);
            }
        } else {
            selected = list.get(0);
        }
        return selected;
    };
}
