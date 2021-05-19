package org.kin.rsocket.service;

import org.kin.framework.utils.CollectionUtils;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.core.RSocketMimeType;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

/**
 * 根据{@link RSocketServiceReference}注解信息注册对应的{@link RSocketServiceReferenceFactoryBean}
 *
 * @author huangjianqin
 * @date 2021/5/19
 */
class RSocketServiceReferenceRegistrar implements ImportBeanDefinitionRegistrar {
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        AnnotationAttributes mapperScanAttrs = AnnotationAttributes.fromMap(importingClassMetadata.getAnnotationAttributes(RSocketServiceReference.class.getName()));
        if (mapperScanAttrs != null) {
            registerBeanDefinition(mapperScanAttrs, registry);
        }
    }

    /**
     * 注册{@link RSocketServiceReferenceFactoryBean}
     */
    void registerBeanDefinition(AnnotationAttributes annoAttrs, BeanDefinitionRegistry registry) {
        BeanDefinitionBuilder beanBuilder = BeanDefinitionBuilder.genericBeanDefinition(RSocketServiceReferenceFactoryBean.class);

        Class<?> serviceInterfaceClass = annoAttrs.getClass("value");
        RSocketServiceReferenceBuilder<?> referenceBuilder = RSocketServiceReferenceBuilder.requester(serviceInterfaceClass);
        String serviceName = annoAttrs.getString("name");
        if (StringUtils.isNotBlank(serviceName)) {
            referenceBuilder.service(serviceName);
        }

        String group = annoAttrs.getString("group");
        if (StringUtils.isNotBlank(group)) {
            referenceBuilder.group(group);
        }

        String version = annoAttrs.getString("version");
        if (StringUtils.isNotBlank(version)) {
            referenceBuilder.version(version);
        }

        int callTimeout = annoAttrs.getNumber("callTimeout");
        if (callTimeout > 0) {
            referenceBuilder.callTimeout(callTimeout);
        }

        String endpoint = annoAttrs.getString("endpoint");
        if (StringUtils.isNotBlank(endpoint)) {
            referenceBuilder.endpoint(endpoint);
        }

        boolean sticky = annoAttrs.getBoolean("sticky");
        if (sticky) {
            referenceBuilder.sticky(sticky);
        }

        RSocketMimeType encodingType = annoAttrs.getEnum("encodingType");
        referenceBuilder.encodingType(encodingType);

        RSocketMimeType[] acceptEncodingTypes = (RSocketMimeType[]) annoAttrs.get("acceptEncodingTypes");
        if (CollectionUtils.isNonEmpty(acceptEncodingTypes)) {
            referenceBuilder.acceptEncodingTypes(acceptEncodingTypes);
        }

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
