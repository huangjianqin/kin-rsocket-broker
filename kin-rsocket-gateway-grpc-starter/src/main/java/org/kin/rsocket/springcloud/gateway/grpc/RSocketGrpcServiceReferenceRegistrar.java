package org.kin.rsocket.springcloud.gateway.grpc;

import io.grpc.BindableService;
import org.kin.framework.utils.CollectionUtils;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
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
public final class RSocketGrpcServiceReferenceRegistrar implements ImportBeanDefinitionRegistrar {
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, @Nonnull BeanDefinitionRegistry registry) {
        AnnotationAttributes annoAttrs = AnnotationAttributes
                .fromMap(importingClassMetadata.getAnnotationAttributes(EnableRSocketGrpcServiceReference.class.getName()));
        if (Objects.nonNull(annoAttrs)) {
            //扫描实现BindableService实现类
            registerScanner(importingClassMetadata, registry, annoAttrs,
                    importingClassMetadata.getClassName() + "#" + RSocketGrpcServiceReferenceRegistryPostProcessor.class.getSimpleName());

            //处理直接配置的rsocket grpc service reference
            AnnotationAttributes[] rsocketServiceReferenceAttrs = annoAttrs.getAnnotationArray("references");
            for (AnnotationAttributes rsocketServiceReferenceAttr : rsocketServiceReferenceAttrs) {
                registerBeanDefinition(rsocketServiceReferenceAttr, registry);
            }
        }
    }

    /**
     * 注册{@link RSocketGrpcServiceReferenceRegistryPostProcessor} bean
     */
    public void registerScanner(AnnotationMetadata importingClassMetadata, @Nonnull BeanDefinitionRegistry registry, AnnotationAttributes annoAttrs, String beanName) {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(RSocketGrpcServiceReferenceRegistryPostProcessor.class);
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

    /**
     * 注册{@link RSocketGrpcServiceReferenceFactoryBean}
     */
    @SuppressWarnings("unchecked")
    public void registerBeanDefinition(AnnotationAttributes annoAttrs, BeanDefinitionRegistry registry) {
        BeanDefinitionBuilder beanBuilder = BeanDefinitionBuilder.genericBeanDefinition(RSocketGrpcServiceReferenceFactoryBean.class);

        Class<? extends BindableService> serviceStubClass = (Class<? extends BindableService>) annoAttrs.get("interfaceClass");
        if (Objects.isNull(serviceStubClass) ||
                Void.class.equals(serviceStubClass) ||
                !BindableService.class.isAssignableFrom(serviceStubClass)) {
            //stub没有定义 | 不是实现了BindableService接口, 则不走这里
            return;
        }

        RSocketGrpcServiceImplBuilder<?> referenceBuilder = RSocketGrpcServiceImplBuilder.stub(serviceStubClass, annoAttrs);

        //factory bean constructor args
        beanBuilder.addConstructorArgValue(referenceBuilder);
        //enable autowire
        beanBuilder.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
        //set lazy init
        beanBuilder.setLazyInit(true);

        //以service name当bean name
        registry.registerBeanDefinition(referenceBuilder.getInterceptor().getService(), beanBuilder.getBeanDefinition());
    }
}
