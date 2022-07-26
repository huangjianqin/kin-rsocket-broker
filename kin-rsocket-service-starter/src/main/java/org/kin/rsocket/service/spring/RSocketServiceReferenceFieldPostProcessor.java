package org.kin.rsocket.service.spring;

import brave.Tracing;
import org.kin.framework.spring.AbstractAnnotationBeanPostProcessor;
import org.kin.rsocket.service.RSocketBrokerClient;
import org.kin.rsocket.service.RSocketServiceProperties;
import org.kin.rsocket.service.RSocketServiceReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.InjectionMetadata;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 处理{@link RSocketServiceReference}注解在Field上的场景
 *
 * @author huangjianqin
 * @date 2022/3/23
 */
@Component
public class RSocketServiceReferenceFieldPostProcessor extends AbstractAnnotationBeanPostProcessor {
    @Autowired
    private RSocketBrokerClient brokerClient;
    @Autowired
    private RSocketServiceProperties rsocketServiceProperties;
    @Autowired(required = false)
    private Tracing tracing;

    @SuppressWarnings("unchecked")
    public RSocketServiceReferenceFieldPostProcessor() {
        super(RSocketServiceReference.class);
    }

    @Override
    protected Object doGetInjectedBean(AnnotationAttributes attributes, Object bean, String beanName,
                                       Class<?> injectedType, InjectionMetadata.InjectedElement injectedElement) {
        return new RSocketServiceReferenceFactoryBean<>(brokerClient, rsocketServiceProperties, tracing, injectedType, attributes).getObject();
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
            if ("interfaceClass".equals(key)) {
                //过滤
                continue;
            }

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
