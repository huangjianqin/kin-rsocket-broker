package org.kin.rsocket.service;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

import java.util.Objects;

/**
 * @author huangjianqin
 * @date 2021/5/19
 * @see RSocketServiceReferenceRegistrar
 */
class RSocketServiceReferencesRegistrar extends RSocketServiceReferenceRegistrar {
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        AnnotationAttributes rsocketServiceReferencesAttrs = AnnotationAttributes
                .fromMap(importingClassMetadata.getAnnotationAttributes(RSocketServiceReferences.class.getName()));
        if (Objects.nonNull(rsocketServiceReferencesAttrs)) {
            AnnotationAttributes[] rsocketServiceReferenceAttrs = rsocketServiceReferencesAttrs.getAnnotationArray("value");
            for (int i = 0; i < rsocketServiceReferenceAttrs.length; i++) {
                registerBeanDefinition(rsocketServiceReferenceAttrs[i], registry);
            }
        }
    }
}
