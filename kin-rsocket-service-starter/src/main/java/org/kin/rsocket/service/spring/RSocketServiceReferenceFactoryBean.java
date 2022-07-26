package org.kin.rsocket.service.spring;

import brave.Tracing;
import org.kin.rsocket.service.RSocketBrokerClient;
import org.kin.rsocket.service.RSocketServiceProperties;
import org.kin.rsocket.service.RSocketServiceReference;
import org.kin.rsocket.service.RSocketServiceReferenceBuilder;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.MethodMetadata;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Objects;

/**
 * 构建rsocket service reference的spring factory bean
 *
 * @author huangjianqin
 * @date 2021/5/19
 */
public final class RSocketServiceReferenceFactoryBean<T> implements FactoryBean<T>, BeanFactoryAware, BeanNameAware, InitializingBean {
    @Autowired
    private RSocketBrokerClient brokerClient;
    @Autowired
    private RSocketServiceProperties rsocketServiceProperties;
    @Autowired(required = false)
    private Tracing tracing;
    private String beanName;
    private ConfigurableListableBeanFactory beanFactory;


    /** 缓存rsocket service reference builder, 创建reference后会clear掉 */
    private RSocketServiceReferenceBuilder<T> builder;
    /** rsocket service 服务reference, 仅仅build一次 */
    private volatile T reference;

    /**
     * 仅仅适用于基于{@link org.springframework.context.annotation.Bean}创建bean的方式
     */
    public RSocketServiceReferenceFactoryBean() {
    }

    /**
     * 暴露给{@link RSocketServiceReferenceFieldPostProcessor}使用, 因为是直接create, 所以无法通过{@link Autowired}获取spring bean
     */
    RSocketServiceReferenceFactoryBean(@Nonnull RSocketBrokerClient brokerClient,
                                       @Nonnull RSocketServiceProperties rsocketServiceProperties,
                                       @Nullable Tracing tracing,
                                       @Nonnull Class<T> claxx,
                                       @Nonnull AnnotationAttributes annoAttrs) {
        this.brokerClient = brokerClient;
        this.rsocketServiceProperties = rsocketServiceProperties;
        this.tracing = tracing;
        initBuilder(claxx, annoAttrs);
    }

    @SuppressWarnings("unchecked")
    private void initBuilder(Class<T> claxx, AnnotationAttributes annoAttrs) {
        builder = RSocketServiceReferenceBuilder.reference(claxx, annoAttrs);
        builder.groupIfEmpty(rsocketServiceProperties.getGroup())
                .versionIfEmpty(rsocketServiceProperties.getVersion());
    }

    @Override
    public T getObject() {
        if (Objects.isNull(reference)) {
            builder.upstreamClusters(brokerClient);
            builder.tracing(tracing);
            reference = builder.build();
            //release
            builder = null;
        }

        return reference;
    }

    @Override
    public Class<?> getObjectType() {
        if (Objects.nonNull(builder)) {
            return builder.getServiceInterface();
        } else {
            return reference.getClass();
        }
    }

    /**
     * 单例
     */
    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public void setBeanName(@Nonnull String name) {
        beanName = name;
    }

    @Override
    public void setBeanFactory(@Nonnull BeanFactory beanFactory) throws BeansException {
        this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
    }

    @Override
    public void afterPropertiesSet() {
        if (Objects.nonNull(builder)) {
            return;
        }

        //仅仅处理通过无参构造方法创建实例的场景
        //获取BeanDefinition
        BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
        if (!(beanDefinition instanceof AnnotatedBeanDefinition)) {
            return;
        }
        AnnotatedBeanDefinition annotatedBeanDefinition = (AnnotatedBeanDefinition) beanDefinition;
        //获取@RSocketServiceReference注解属性
        MethodMetadata factoryMethodMetadata = annotatedBeanDefinition.getFactoryMethodMetadata();
        if (Objects.isNull(factoryMethodMetadata)) {
            throw new IllegalArgumentException(String.format("unavailable to create %s instance without @Bean", RSocketServiceReferenceFactoryBean.class.getSimpleName()));
        }
        Map<String, Object> annoAttrsMap = factoryMethodMetadata.getAnnotationAttributes(RSocketServiceReference.class.getName());
        if (Objects.isNull(annoAttrsMap)) {
            throw new IllegalArgumentException("bean factory method is not annotated with @" + RSocketServiceReference.class.getSimpleName());
        }
        //通过@RSocketServiceReference.interfaceClass()获取bean class
        @SuppressWarnings("unchecked")
        Class<T> claxx = (Class<T>) annoAttrsMap.get("interfaceClass");
        if (Objects.isNull(claxx)) {
            throw new IllegalArgumentException(RSocketServiceReference.class.getSimpleName() + "does not set `interfaceClass` value");
        }
        //初始化builder
        initBuilder(claxx, new AnnotationAttributes(annoAttrsMap));
    }
}
