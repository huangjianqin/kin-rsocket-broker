package org.kin.rsocket.core;

/**
 * 自定义rsocket bind额外逻辑
 *
 * @author huangjianqin
 * @date 2021/3/29
 */
@FunctionalInterface
public interface RSocketBinderCustomizer {
    /**
     * 自定义额外的rsocket binder builder 逻辑
     */
    void customize(RSocketBinder.Builder builder);
}
