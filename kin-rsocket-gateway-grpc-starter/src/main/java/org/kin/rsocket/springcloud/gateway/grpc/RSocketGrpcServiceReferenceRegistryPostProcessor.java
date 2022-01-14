package org.kin.rsocket.springcloud.gateway.grpc;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.StringUtils;

import javax.annotation.Nonnull;

/**
 * 在bean factory处理前, 创建{@link RSocketGrpcServiceReferenceScanner}并启动扫描{@link io.grpc.BindableService}实现类并注册为bean
 *
 * @author huangjianqin
 * @date 2022/1/12
 */
public final class RSocketGrpcServiceReferenceRegistryPostProcessor implements BeanDefinitionRegistryPostProcessor {
    /** 扫描package classpath的路径集合, 以,分隔 */
    private final String basePackage;

    public RSocketGrpcServiceReferenceRegistryPostProcessor(String basePackage) {
        this.basePackage = basePackage;
    }

    @Override
    public void postProcessBeanFactory(@Nonnull ConfigurableListableBeanFactory beanFactory) throws BeansException {
        //do nothing
    }

    @Override
    public void postProcessBeanDefinitionRegistry(@Nonnull BeanDefinitionRegistry registry) throws BeansException {
        RSocketGrpcServiceReferenceScanner scanner = new RSocketGrpcServiceReferenceScanner(registry);
        scanner.scan(StringUtils.tokenizeToStringArray(this.basePackage, ConfigurableApplicationContext.CONFIG_LOCATION_DELIMITERS));
    }
}
