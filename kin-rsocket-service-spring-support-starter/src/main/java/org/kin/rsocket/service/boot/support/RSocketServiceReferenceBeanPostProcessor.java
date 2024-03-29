package org.kin.rsocket.service.boot.support;

import org.kin.framework.spring.beans.AbstractAnnotationBeanPostProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.InjectionMetadata;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.StringJoiner;

/**
 * @author huangjianqin
 * @date 2022/3/23
 */
@Component
public class RSocketServiceReferenceBeanPostProcessor extends AbstractAnnotationBeanPostProcessor {
    @Autowired
    private RSocketRequester.Builder requesterBuilder;
    @Autowired(required = false)
    private RSocketRequester requester;
    @Autowired(required = false)
    private RSocketServiceDiscoveryRegistry registry;
    @Autowired(required = false)
    private LoadbalanceStrategyFactory loadbalanceStrategyFactory;

    public RSocketServiceReferenceBeanPostProcessor() {
        super(RSocketServiceReference.class);
    }

    @Override
    protected Object doGetInjectedBean(AnnotationAttributes attributes, Object bean, String beanName,
                                       Class<?> injectedType, InjectionMetadata.InjectedElement injectedElement) throws Exception {
        return new RSocketServiceReferenceFactoryBean<>(injectedType, attributes, requesterBuilder, requester, registry, loadbalanceStrategyFactory).getObject();
    }

    @Override
    protected String buildInjectedObjectCacheKey(AnnotationAttributes attributes, Object bean, String beanName,
                                                 Class<?> injectedType, InjectionMetadata.InjectedElement injectedElement) {
        StringBuilder sb = new StringBuilder();
        StringJoiner sj = new StringJoiner(",");
        sb.append(injectedType.getName());
        sb.append("@").append(RSocketServiceReference.class.getSimpleName()).append("{");
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            String valueStr;
            if (value.getClass().isArray()) {
                valueStr = Arrays.toString((Object[]) value);
            } else {
                valueStr = value.toString();
            }

            sj.add(key).add("=").add(valueStr);
        }
        sb.append(sj).append("}");
        return sb.toString();
    }
}
