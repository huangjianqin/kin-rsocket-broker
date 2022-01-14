package org.kin.spring.rsocket.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.messaging.rsocket.RSocketRequester;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author huangjianqin
 * @date 2021/5/19
 */
public final class SpringRSocketServiceReferenceFactoryBean<T> extends AbstractFactoryBean<T> {
    /** 底层remote requester */
    @Autowired
    private RSocketRequester rsocketRequester;
    /** 服务接口 */
    private final Class<T> serviceInterface;
    /** rsocket service 服务reference, 仅仅build一次 */
    private volatile T reference;

    @SuppressWarnings("unchecked")
    public SpringRSocketServiceReferenceFactoryBean(Class<T> serviceInterface) {
        if (!serviceInterface.isInterface()) {
            throw new IllegalArgumentException(
                    String.format("class '%s' must be interface", serviceInterface.getName()));
        }
        SpringRSocketServiceReference rsocketServiceReference = AnnotationUtils.findAnnotation(serviceInterface, SpringRSocketServiceReference.class);
        if (Objects.isNull(rsocketServiceReference)) {
            throw new IllegalArgumentException(
                    String.format("scanner find interface '%s' with @RSocketServiceReference, but it actually doesn't has it", serviceInterface.getName()));
        }

        AnnotationAttributes annoAttrs = AnnotationAttributes.fromMap(AnnotationUtils.getAnnotationAttributes(rsocketServiceReference));
        Class<T> serviceInterfaceClass = (Class<T>) annoAttrs.get("interfaceClass");
        if (Objects.nonNull(serviceInterfaceClass) && !Void.class.equals(serviceInterfaceClass)) {
            //定义了接口, 不允许的
            throw new IllegalArgumentException(
                    String.format("interface '%s' with @SpringRSocketServiceReference has define interface class", serviceInterface.getName()));
        }
        this.serviceInterface = serviceInterface;
    }

    @Override
    public Class<?> getObjectType() {
        return serviceInterface;
    }

    @Nonnull
    @Override
    protected T createInstance() {
        if (Objects.isNull(reference)) {
            reference = builder().build();
        }

        return reference;
    }

    private SpringRSocketServiceReferenceBuilder<T> builder() {
        SpringRSocketServiceReference rsocketServiceReference = AnnotationUtils.findAnnotation(serviceInterface, SpringRSocketServiceReference.class);
        if (Objects.isNull(rsocketServiceReference)) {
            throw new IllegalArgumentException(
                    String.format("scanner find interface '%s' with @SpringRSocketServiceReference, but it actually doesn't has it", serviceInterface.getName()));
        }

        SpringRSocketServiceReferenceBuilder<T> builder = SpringRSocketServiceReferenceBuilder.requester(rsocketRequester, serviceInterface);
        builder.service(rsocketServiceReference.service());
        builder.timeout(rsocketServiceReference.callTimeout(), TimeUnit.SECONDS);
        return builder;
    }

    /**
     * 单例
     */
    @Override
    public boolean isSingleton() {
        return true;
    }
}
