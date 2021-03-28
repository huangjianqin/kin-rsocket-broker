package org.kin.rsocket.core;

/**
 * @author huangjianqin
 * @date 2021/3/29
 */
@FunctionalInterface
public interface RSocketBinderBuilderCustomizer {
    /**
     * 自定义额外的rsocket binder builder 逻辑
     */
    void customize(RSocketBinder.Builder builder);
}
