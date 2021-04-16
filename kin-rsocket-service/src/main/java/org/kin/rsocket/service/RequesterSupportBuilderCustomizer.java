package org.kin.rsocket.service;

/**
 * @author huangjianqin
 * @date 2021/3/28
 */
@FunctionalInterface
public interface RequesterSupportBuilderCustomizer {
    /**
     * 自定义额外的requester builder 逻辑
     */
    void customize(RequesterSupportImpl requesterSupport);
}
