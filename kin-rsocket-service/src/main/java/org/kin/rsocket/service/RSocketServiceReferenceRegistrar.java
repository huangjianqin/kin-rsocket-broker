package org.kin.rsocket.service;

import org.kin.framework.utils.CollectionUtils;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author huangjianqin
 * @date 2021/5/19
 * @see EnableRSocketServiceReference
 */
class RSocketServiceReferenceRegistrar implements ImportBeanDefinitionRegistrar {
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        AnnotationAttributes annoAttrs = AnnotationAttributes
                .fromMap(importingClassMetadata.getAnnotationAttributes(EnableRSocketServiceReference.class.getName()));
        if (Objects.nonNull(annoAttrs)) {
            //扫描注解有@RSocketServiceReference的接口
            registerScanner(importingClassMetadata, registry, annoAttrs,
                    importingClassMetadata.getClassName() + "#" + RSocketServiceReferenceRegistryPostProcessor.class.getSimpleName());

            //处理直接配置的rsocket service reference
            AnnotationAttributes[] rsocketServiceReferenceAttrs = annoAttrs.getAnnotationArray("references");
            for (int i = 0; i < rsocketServiceReferenceAttrs.length; i++) {
                registerBeanDefinition(rsocketServiceReferenceAttrs[i], registry);
            }
        }
    }

    /**
     * 注册{@link RSocketServiceReferenceRegistryPostProcessor} bean
     */
    void registerScanner(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry, AnnotationAttributes annoAttrs, String beanName) {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(RSocketServiceReferenceRegistryPostProcessor.class);
        List<String> basePackages = new ArrayList<>();

        String[] basePackageArr = annoAttrs.getStringArray("basePackages");
        if (CollectionUtils.isNonEmpty(basePackageArr)) {
            //过滤空串
            basePackages.addAll(Arrays.stream(basePackageArr).filter(org.springframework.util.StringUtils::hasText).collect(Collectors.toList()));
            //如果是class name, 则取其package class path
            basePackages.addAll(Arrays.stream(basePackageArr).map(ClassUtils::getPackageName).collect(Collectors.toList()));
        }
        //@Import(RSocketServiceReferenceRegistrar.class)的声明类package class path
        basePackages.add(ClassUtils.getPackageName(importingClassMetadata.getClassName()));

        builder.addConstructorArgValue(org.springframework.util.StringUtils.collectionToCommaDelimitedString(basePackages));

        registry.registerBeanDefinition(beanName, builder.getBeanDefinition());
    }

    /**
     * 注册{@link RSocketServiceReferenceFactoryBean}
     */
    void registerBeanDefinition(AnnotationAttributes annoAttrs, BeanDefinitionRegistry registry) {
        BeanDefinitionBuilder beanBuilder = BeanDefinitionBuilder.genericBeanDefinition(RSocketServiceReferenceFactoryBean.class);

        Class<?> serviceInterfaceClass = (Class<?>) annoAttrs.get("interfaceClass");
        if (Objects.isNull(serviceInterfaceClass) || Void.class.equals(serviceInterfaceClass)) {
            //接口没有定义, 则不走这里
            return;
        }

        RSocketServiceReferenceBuilder<?> referenceBuilder = RSocketServiceReferenceBuilder.requester(serviceInterfaceClass, annoAttrs);

        //factory bean constructor args
        beanBuilder.addConstructorArgValue(referenceBuilder);
        //enable autowire
        beanBuilder.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
        //set lazy init
        beanBuilder.setLazyInit(true);

        //以service name当bean name
        registry.registerBeanDefinition(referenceBuilder.getService(), beanBuilder.getBeanDefinition());
    }
}
