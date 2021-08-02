package org.kin.rsocket.service;

/**
 * 自定义rsocket connector额外逻辑
 *
 * @author huangjianqin
 * @date 2021/3/28
 */
@FunctionalInterface
public interface RSocketRequesterSupportCustomizer {
    /**
     * 自定义额外的requester builder 逻辑
     */
    void customize(RSocketRequesterSupportImpl requesterSupport);
}
