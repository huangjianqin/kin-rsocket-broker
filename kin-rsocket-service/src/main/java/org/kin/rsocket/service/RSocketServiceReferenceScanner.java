package org.kin.rsocket.service;

import org.kin.framework.log.LoggerOprs;
import org.kin.framework.utils.StringUtils;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.Set;

/**
 * 根据指定规则, 扫描带{@link RSocketServiceReference}注解的interface class并注册为bean
 *
 * @author huangjianqin
 * @date 2021/5/20
 */
public class RSocketServiceReferenceScanner extends ClassPathBeanDefinitionScanner implements LoggerOprs {
    public RSocketServiceReferenceScanner(BeanDefinitionRegistry registry) {
        super(registry);
        registerFilters();
    }

    /**
     * 注册filter, 指定classpath扫描策略
     */
    private void registerFilters() {
        // 扫描@RSocketServiceReference注解的top-level class
        addIncludeFilter(new AnnotationTypeFilter(RSocketServiceReference.class));

        // exclude package-info.java
        addExcludeFilter((metadataReader, metadataReaderFactory) -> {
            String className = metadataReader.getClassMetadata().getClassName();
            return className.endsWith("package-info");
        });
    }

    @Override
    protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
        Set<BeanDefinitionHolder> beanDefinitions = super.doScan(basePackages);

        if (beanDefinitions.isEmpty()) {
            info("no rsocket service reference interface was found in classpath !!!");
        } else {
            processBeanDefinitions(beanDefinitions);
        }

        return beanDefinitions;
    }

    /**
     * 处理扫描到的rsocket service reference bean
     */
    private void processBeanDefinitions(Set<BeanDefinitionHolder> beanDefinitions) {
        for (BeanDefinitionHolder holder : beanDefinitions) {
            GenericBeanDefinition definition = (GenericBeanDefinition) holder.getBeanDefinition();
            //factory bean constructor args
            String beanClassName = definition.getBeanClassName();
            if (StringUtils.isBlank(beanClassName)) {
                throw new IllegalStateException("rsocket service reference interface class is null");
            }
            definition.getConstructorArgumentValues().addGenericArgumentValue(beanClassName);
            //factory bean class
            definition.setBeanClass(RSocketServiceReferenceFactoryBean.class);
            //enable autowire
            definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
            //set lazy init
            definition.setLazyInit(true);
        }
    }

    @Override
    protected boolean checkCandidate(String beanName, BeanDefinition beanDefinition) throws IllegalStateException {
        if (super.checkCandidate(beanName, beanDefinition)) {
            return true;
        } else {
            warn("skipping RSocketServiceReferenceFactoryBean with name '" + beanName + "' and '"
                    + beanDefinition.getBeanClassName() + "' interface" + ". bean already defined with the same name!");
            return false;
        }
    }

    @Override
    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
        //@RSocketServiceReference && 接口 && top-level class
        AnnotationMetadata metadata = beanDefinition.getMetadata();
        return metadata.getAnnotationTypes().contains(RSocketServiceReference.class.getName()) &&
                metadata.isInterface() &&
                metadata.isIndependent();
    }
}