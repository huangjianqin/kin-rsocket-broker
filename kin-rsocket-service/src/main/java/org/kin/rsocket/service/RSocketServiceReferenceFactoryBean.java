package org.kin.rsocket.service;

import brave.Tracing;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationUtils;

import java.util.Objects;

/**
 * @author huangjianqin
 * @date 2021/5/19
 */
class RSocketServiceReferenceFactoryBean<T> extends AbstractFactoryBean<T> {
    @Autowired
    private RSocketServiceRequester requester;
    /** 缓存rsocket service reference builder */
    private final RSocketServiceReferenceBuilder<T> builder;
    /** rsocket service 服务reference, 仅仅build一次 */
    private volatile T reference;
    @Autowired(required = false)
    private Tracing tracing;

    RSocketServiceReferenceFactoryBean(RSocketServiceReferenceBuilder<T> builder) {
        this.builder = builder;
    }

    @SuppressWarnings("unchecked")
    RSocketServiceReferenceFactoryBean(Class<T> claxx) {
        if (!claxx.isInterface()) {
            throw new IllegalArgumentException(
                    String.format("class '%s' must be interface", claxx.getName()));
        }
        RSocketServiceReference rsocketServiceReference = AnnotationUtils.findAnnotation(claxx, RSocketServiceReference.class);
        if (Objects.isNull(rsocketServiceReference)) {
            throw new IllegalArgumentException(
                    String.format("scanner find interface '%s' with @RSocketServiceReference, but it actually doesn't has it", claxx.getName()));
        }

        AnnotationAttributes annoAttrs = AnnotationAttributes.fromMap(AnnotationUtils.getAnnotationAttributes(rsocketServiceReference));
        Class<T> serviceInterfaceClass = (Class<T>) annoAttrs.get("interfaceClass");
        if (Objects.nonNull(serviceInterfaceClass) && !Void.class.equals(serviceInterfaceClass)) {
            //定义了接口, 不允许的
            throw new IllegalArgumentException(
                    String.format("interface '%s' with @RSocketServiceReference has define interface class", claxx.getName()));
        }

        builder = RSocketServiceReferenceBuilder.requester(claxx, annoAttrs);
    }

    @Override
    public Class<?> getObjectType() {
        return builder.getServiceInterface();
    }

    @Override
    protected T createInstance() throws Exception {
        if (Objects.isNull(reference)) {
            builder.upstreamClusterManager(requester);
            builder.tracing(tracing);
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
