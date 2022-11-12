package org.kin.rsocket.gateway.grpc;

import org.kin.framework.utils.CollectionUtils;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author huangjianqin
 * @date 2022/1/11
 */
public final class GrpcServiceRSocketImplementationRegistrar implements ImportBeanDefinitionRegistrar {
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, @Nonnull BeanDefinitionRegistry registry) {
        AnnotationAttributes annoAttrs = AnnotationAttributes
                .fromMap(importingClassMetadata.getAnnotationAttributes(EnableGrpcServiceRSocketImpl.class.getName()));
        if (Objects.nonNull(annoAttrs)) {
            //扫描实现BindableService实现类
            registerScanner(importingClassMetadata, registry, annoAttrs,
                    importingClassMetadata.getClassName() + "#" + GrpcServiceRSocketImplementationRegistryPostProcessor.class.getSimpleName());
        }
    }

    /**
     * 注册{@link GrpcServiceRSocketImplementationRegistryPostProcessor} bean
     */
    public void registerScanner(AnnotationMetadata importingClassMetadata, @Nonnull BeanDefinitionRegistry registry, AnnotationAttributes annoAttrs, String beanName) {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(GrpcServiceRSocketImplementationRegistryPostProcessor.class);
        List<String> basePackages = new ArrayList<>();

        String[] basePackageArr = annoAttrs.getStringArray("basePackages");
        if (CollectionUtils.isNonEmpty(basePackageArr)) {
            //过滤空串
            basePackages.addAll(Arrays.stream(basePackageArr).filter(org.springframework.util.StringUtils::hasText).collect(Collectors.toList()));
            //如果是class name, 则取其package class path
            basePackages.addAll(Arrays.stream(basePackageArr).map(ClassUtils::getPackageName).collect(Collectors.toList()));
        }
        //@Import(GrpcRSocketServiceReferenceRegistrar.class)的声明类package class path
        basePackages.add(ClassUtils.getPackageName(importingClassMetadata.getClassName()));

        builder.addConstructorArgValue(org.springframework.util.StringUtils.collectionToCommaDelimitedString(basePackages));

        registry.registerBeanDefinition(beanName, builder.getBeanDefinition());
    }
}
