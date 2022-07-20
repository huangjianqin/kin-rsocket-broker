package org.kin.rsocket.spring.support;

import org.kin.framework.utils.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.MethodMetadata;
import org.springframework.messaging.rsocket.RSocketRequester;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author huangjianqin
 * @date 2021/5/19
 */
public final class SpringRSocketServiceReferenceFactoryBean<T> implements FactoryBean<T>, BeanFactoryAware,
        BeanNameAware, InitializingBean, DisposableBean {
    /**
     * spring创建的rsocket requester builder
     * prototype类型
     *
     * @see RSocketRequesterAutoConfiguration
     */
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
    private String beanName;
    private ConfigurableListableBeanFactory beanFactory;

    /** 服务接口 */
    private Class<T> serviceInterface;
    /** 服务名 */
    private String serviceName;
    /** naming service上注册的rsocket service application name */
    private String appName;
    /** call timeout */
    private int callTimeout;
    /** 支持load balance rsocket requester */
    private volatile RSocketRequester loadBalanceRequester;

    /** rsocket service 服务reference, 仅仅build一次 */
    private volatile T reference;

    public SpringRSocketServiceReferenceFactoryBean() {
    }

    SpringRSocketServiceReferenceFactoryBean(Class<T> serviceInterface, AnnotationAttributes annoAttrs,
                                             RSocketRequester.Builder requesterBuilder, RSocketRequester requester,
                                             SpringRSocketServiceDiscoveryRegistry registry, LoadbalanceStrategyFactory loadbalanceStrategyFactory) {
        if (!serviceInterface.isInterface()) {
            throw new IllegalArgumentException(
                    String.format("class '%s' must be interface", serviceInterface.getName()));
        }

        this.serviceInterface = serviceInterface;
        this.requesterBuilder = requesterBuilder;
        this.requester = requester;
        this.registry = registry;
        this.loadbalanceStrategyFactory = loadbalanceStrategyFactory;
        parseAnno(annoAttrs);
    }

    private void parseAnno(AnnotationAttributes annoAttrs) {
        //此处的service name可能会包括app name或者app version信息
        serviceName = annoAttrs.getString("service");
        if (StringUtils.isBlank(serviceName)) {
            serviceName = serviceInterface.getName();
        }

        appName = annoAttrs.getString("appName");
        if (StringUtils.isBlank(appName)) {
            appName = serviceName.substring(0, serviceName.lastIndexOf("."));
        }
        callTimeout = annoAttrs.getNumber("callTimeout");
    }

    @Override
    public T getObject() {
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
            //此处必须只取service name, 不然在broker模式下, route metadata会存在异常
            builder.service(takeRealServiceName(serviceName));
            builder.timeout(callTimeout, TimeUnit.SECONDS);

            reference = builder.build();
        }

        return reference;
    }

    @Override
    public Class<?> getObjectType() {
        return serviceInterface;
    }

    /**
     * 提取真正的service name, 不包含app name或者app version信息
     */
    private String takeRealServiceName(String serviceName) {
        //移除app instance name
        if (serviceName.contains(":")) {
            serviceName = serviceName.substring(serviceName.indexOf(":") + 1);
        }

        //移除实例版本信息
        String mayBeVersion = serviceName.substring(serviceName.lastIndexOf("-") + 1);
        if (StringUtils.isNumeric(mayBeVersion)) {
            serviceName = serviceName.substring(0, serviceName.lastIndexOf("-"));
        }

        return serviceName;
    }

    /**
     * 单例
     */
    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public void destroy() {
        if (Objects.nonNull(loadBalanceRequester)) {
            //如果构建了支持load balance的requester, 则需要手动shutdown requester
            loadBalanceRequester.dispose();
        }
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
        if (Objects.nonNull(serviceInterface)) {
            return;
        }

        //仅仅处理通过无参构造方法创建实例的场景
        //获取BeanDefinition
        BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
        if (!(beanDefinition instanceof AnnotatedBeanDefinition)) {
            return;
        }
        AnnotatedBeanDefinition annotatedBeanDefinition = (AnnotatedBeanDefinition) beanDefinition;
        //获取@SpringRSocketServiceReference注解属性
        MethodMetadata factoryMethodMetadata = annotatedBeanDefinition.getFactoryMethodMetadata();
        if (Objects.isNull(factoryMethodMetadata)) {
            throw new IllegalArgumentException(String.format("unavailable to create %s instance without @Bean", SpringRSocketServiceReferenceFactoryBean.class.getSimpleName()));
        }
        Map<String, Object> annoAttrsMap = factoryMethodMetadata.getAnnotationAttributes(SpringRSocketServiceReference.class.getName());
        if (Objects.isNull(annoAttrsMap)) {
            throw new IllegalArgumentException("bean factory method is not annotated with @" + SpringRSocketServiceReference.class.getSimpleName());
        }
        //通过@RSpringRSocketServiceReference.interfaceClass()获取bean class
        //noinspection unchecked
        serviceInterface = (Class<T>) annoAttrsMap.get("interfaceClass");
        if (Objects.isNull(serviceInterface)) {
            throw new IllegalArgumentException(SpringRSocketServiceReference.class.getSimpleName() + "does not set `interfaceClass` value");
        }
        //初始化builder
        parseAnno(new AnnotationAttributes(annoAttrsMap));
    }
}
