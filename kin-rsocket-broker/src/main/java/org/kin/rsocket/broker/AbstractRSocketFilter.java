package org.kin.rsocket.broker;

import reactor.core.publisher.Mono;

/**
 * @author huangjianqin
 * @date 2021/3/27
 */
public abstract class AbstractRSocketFilter {
    /** filter是否开启 */
    private boolean enabled = true;

    /**
     * @return filter是否开启
     */
    public final boolean isEnabled() {
        return enabled;
    }

    /**
     * 开启/下线 filter
     */
    public void updateEnable(boolean enable) {
        this.enabled = enable;
    }

    /**
     * 是否filter
     */
    public abstract Mono<Boolean> shouldFilter(RSocketFilterContext exchange);

    /**
     * 执行filter逻辑
     */
    public abstract Mono<Void> filter(RSocketFilterContext exchange);

    /**
     * filter name or description
     */
    public abstract String name();
}
