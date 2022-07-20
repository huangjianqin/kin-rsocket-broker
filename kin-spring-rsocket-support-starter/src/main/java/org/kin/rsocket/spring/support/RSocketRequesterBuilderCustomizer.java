package org.kin.rsocket.spring.support;

import org.springframework.messaging.rsocket.RSocketRequester;

/**
 * 自定义{@link RSocketRequester.Builder}自定义额外逻辑
 *
 * @author huangjianqin
 * @date 2022/3/20
 */
@FunctionalInterface
public interface RSocketRequesterBuilderCustomizer {
    /**
     * 自定义{@link RSocketRequester.Builder}额外参数
     */
    void customize(RSocketRequester.Builder builder, String appName, String serviceName);
}
