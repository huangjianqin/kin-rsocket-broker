package org.kin.rsocket.springcloud.service;

import org.kin.rsocket.service.RSocketServiceReference;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.StringUtils;

import javax.annotation.Nonnull;

/**
 * 在bean factory处理前, 创建{@link RSocketServiceReferenceScanner}并启动扫描带{@link RSocketServiceReference}注解的interface class并注册为bean
 *
 * @author huangjianqin
 * @date 2021/5/20
 */
public final class RSocketServiceReferenceRegistryPostProcessor implements BeanDefinitionRegistryPostProcessor {
    /** 扫描package classpath的路径集合, 以,分隔 */
    private final String basePackage;

    public RSocketServiceReferenceRegistryPostProcessor(String basePackage) {
        this.basePackage = basePackage;
    }

    @Override
    public void postProcessBeanFactory(@Nonnull ConfigurableListableBeanFactory beanFactory) throws BeansException {
        //do nothing
    }

    @Override
    public void postProcessBeanDefinitionRegistry(@Nonnull BeanDefinitionRegistry registry) throws BeansException {
        RSocketServiceReferenceScanner scanner = new RSocketServiceReferenceScanner(registry);
        scanner.scan(StringUtils.tokenizeToStringArray(this.basePackage, ConfigurableApplicationContext.CONFIG_LOCATION_DELIMITERS));
    }
}