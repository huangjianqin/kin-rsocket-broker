package org.kin.rsocket.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AbstractFactoryBean;

import java.util.Objects;

/**
 * @author huangjianqin
 * @date 2021/5/19
 */
class RSocketServiceReferenceFactoryBean<T> extends AbstractFactoryBean<T> {
    @Autowired
    private UpstreamClusterManager upstreamClusterManager;
    /** 缓存rsocket service reference builder */
    private final RSocketServiceReferenceBuilder<T> builder;
    /** rsocket service 服务reference, 仅仅build一次 */
    private volatile T reference;

    RSocketServiceReferenceFactoryBean(RSocketServiceReferenceBuilder<T> builder) {
        this.builder = builder;
    }

    @Override
    public Class<?> getObjectType() {
        return builder.getServiceInterface();
    }

    @Override
    protected T createInstance() throws Exception {
        if (Objects.isNull(reference)) {
            builder.upstreamClusterManager(upstreamClusterManager);
            reference = builder.build();
        }

        return reference;
    }

    /**
     * 单例
     */
    @Override
    public boolean isSingleton() {
        return true;
    }
}
