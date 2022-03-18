package org.kin.spring.rsocket.support;

import org.kin.framework.utils.StringUtils;
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
    /** 服务接口 */
    private final Class<T> serviceInterface;

    /** spring创建的rsocket requester builder */
    @Autowired
    private RSocketRequester.Builder requesterBuilder;
    /** spring创建的rsocket requester */
    @Autowired(required = false)
    private RSocketRequester requester;

    /** 基于服务发现的rsocket service instance注册中心, 用于获取loadbalance rsocket requester */
    @Autowired(required = false)
    private SpringRSocketServiceDiscoveryRegistry registry;
    /** 使用自定义的负载均衡策略 */
    @Autowired(required = false)
    private LoadbalanceStrategyFactory loadbalanceStrategyFactory;
    /** 支持load balance rsocket requester */
    private volatile RSocketRequester loadBalanceRequester;

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
        SpringRSocketServiceReference rsocketServiceReference = AnnotationUtils.findAnnotation(serviceInterface, SpringRSocketServiceReference.class);
        if (Objects.isNull(rsocketServiceReference)) {
            throw new IllegalArgumentException(
                    String.format("scanner find interface '%s' with @SpringRSocketServiceReference, but it actually doesn't has it", serviceInterface.getName()));
        }
        String serviceName = rsocketServiceReference.service();
        if (StringUtils.isBlank(serviceName)) {
            serviceName = serviceInterface.getName();
        }
        String appName = rsocketServiceReference.appName();

        if (Objects.isNull(reference)) {
            RSocketRequester rsocketRequester;
            if (Objects.nonNull(registry)) {
                //开启了服务发现, 则创建支持load balance的requester
                loadBalanceRequester = registry.createLoadBalanceRSocketRequester(appName, serviceName, requesterBuilder, loadbalanceStrategyFactory);
                rsocketRequester = loadBalanceRequester;
            } else {
                //没有开启服务发现
                rsocketRequester = this.requester;
            }

            SpringRSocketServiceReferenceBuilder<T> builder = SpringRSocketServiceReferenceBuilder.requester(rsocketRequester, serviceInterface);
            builder.service(serviceName);
            builder.timeout(rsocketServiceReference.callTimeout(), TimeUnit.SECONDS);

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

    @Override
    public void destroy() throws Exception {
        super.destroy();

        if (Objects.nonNull(loadBalanceRequester)) {
            //如果构建了支持load balance的requester, 则需要手动shutdown requester
            loadBalanceRequester.dispose();
        }
    }
}
