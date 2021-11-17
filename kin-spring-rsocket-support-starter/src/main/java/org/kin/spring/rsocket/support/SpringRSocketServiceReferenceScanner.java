package org.kin.spring.rsocket.support;

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

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * 根据指定规则, 扫描带{@link SpringRSocketServiceReference}注解的interface class并注册为bean
 *
 * @author huangjianqin
 * @date 2021/5/20
 */
public class SpringRSocketServiceReferenceScanner extends ClassPathBeanDefinitionScanner implements LoggerOprs {
    public SpringRSocketServiceReferenceScanner(BeanDefinitionRegistry registry) {
        super(registry);
        registerFilters();
    }

    /**
     * 注册filter, 指定classpath扫描策略
     */
    private void registerFilters() {
        // 扫描@SpringRSocketServiceReference注解的top-level class
        addIncludeFilter(new AnnotationTypeFilter(SpringRSocketServiceReference.class));

        // exclude package-info.java
        addExcludeFilter((metadataReader, metadataReaderFactory) -> {
            String className = metadataReader.getClassMetadata().getClassName();
            return className.endsWith("package-info");
        });
    }

    @Nonnull
    @Override
    protected Set<BeanDefinitionHolder> doScan(@Nonnull String... basePackages) {
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
                throw new IllegalStateException("spring rsocket service reference interface class is null");
            }
            definition.getConstructorArgumentValues().addGenericArgumentValue(beanClassName);
            //factory bean class
            definition.setBeanClass(SpringRSocketServiceReferenceFactoryBean.class);
            //enable autowire
            definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
            //set lazy init
            definition.setLazyInit(true);
        }
    }

    @Override
    protected boolean checkCandidate(@Nonnull String beanName, @Nonnull BeanDefinition beanDefinition) throws IllegalStateException {
        if (super.checkCandidate(beanName, beanDefinition)) {
            return true;
        } else {
            warn("skipping SpringRSocketServiceReferenceFactoryBean with name '" + beanName + "' and '"
                    + beanDefinition.getBeanClassName() + "' interface" + ". bean already defined with the same name!");
            return false;
        }
    }

    @Override
    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
        //@SpringRSocketServiceReference && 接口 && top-level class
        AnnotationMetadata metadata = beanDefinition.getMetadata();
        return metadata.getAnnotationTypes().contains(SpringRSocketServiceReference.class.getName()) &&
                metadata.isInterface() &&
                metadata.isIndependent();
    }
}