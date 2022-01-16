package org.kin.rsocket.springcloud.gateway.grpc;

import io.grpc.BindableService;
import org.kin.framework.log.LoggerOprs;
import org.kin.framework.utils.ExceptionUtils;
import org.lognet.springboot.grpc.GRpcService;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AssignableTypeFilter;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Set;

/**
 * @author huangjianqin
 * @date 2022/1/12
 */
public final class RSocketGrpcServiceReferenceScanner extends ClassPathBeanDefinitionScanner implements LoggerOprs {
    public RSocketGrpcServiceReferenceScanner(BeanDefinitionRegistry registry) {
        super(registry);
        registerFilters();
    }

    /**
     * 注册filter, 指定classpath扫描策略
     */
    private void registerFilters() {
        // 扫描BindableService实现类
        addIncludeFilter(new AssignableTypeFilter(BindableService.class));

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
            info("no rsocket rpc service stub was found in classpath !!!");
        } else {
            processBeanDefinitions(beanDefinitions);
        }

        return beanDefinitions;
    }

    /**
     * 处理扫描到的{@link BindableService}实现类
     * 不能lazy load, 不然grpc-spring-boot-starter扫描不到{@link GRpcService}
     */
    private void processBeanDefinitions(Set<BeanDefinitionHolder> beanDefinitions) {
        for (BeanDefinitionHolder holder : beanDefinitions) {
            GenericBeanDefinition definition = (GenericBeanDefinition) holder.getBeanDefinition();
            //factory bean constructor args
            String beanClassName = definition.getBeanClassName();
            //noinspection ConstantConditions
            definition.getConstructorArgumentValues().addGenericArgumentValue(beanClassName);
            //factory bean class
            definition.setBeanClass(RSocketGrpcServiceReferenceFactoryBean.class);
            //enable autowire
            definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
        }
    }

    @Override
    protected boolean checkCandidate(@Nonnull String beanName, @Nonnull BeanDefinition beanDefinition) throws IllegalStateException {
        if (super.checkCandidate(beanName, beanDefinition)) {
            return true;
        } else {
            warn("skipping RSocketGrpcServiceReferenceFactoryBean with name '" + beanName + "' and '"
                    + beanDefinition.getBeanClassName() + "' stub" + ". bean already defined with the same name!");
            return false;
        }
    }

    @Override
    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
        //BindableService实现类
        AnnotationMetadata metadata = beanDefinition.getMetadata();
        String className = metadata.getClassName();
        Class<?> claxx = null;
        try {
            claxx = Class.forName(className);
        } catch (ClassNotFoundException e) {
            ExceptionUtils.throwExt(e);
        }

        Class<?> declaringClass = claxx.getDeclaringClass();
        if (Objects.isNull(declaringClass) || !declaringClass.getSimpleName().startsWith("Reactor")) {
            //reactor grpc生成出来的都是以Reactor开头, 故这里写死类名过滤
            return false;
        }
        return BindableService.class.isAssignableFrom(claxx) && metadata.isIndependent();
    }
}