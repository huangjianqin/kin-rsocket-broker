package org.kin.rsocket.core;

/**
 * 自定义{@link RSocketBinder.Builder}额外逻辑
 *
 * @author huangjianqin
 * @date 2021/3/29
 */
@FunctionalInterface
public interface RSocketBinderCustomizer {
    /**
     * 自定义{@link RSocketBinder.Builder}额外逻辑
     */
    void customize(RSocketBinder.Builder builder);
}
