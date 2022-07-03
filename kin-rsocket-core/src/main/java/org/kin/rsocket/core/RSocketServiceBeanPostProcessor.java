package org.kin.rsocket.core;

import org.kin.framework.utils.CollectionUtils;
import org.kin.framework.utils.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.MethodMetadata;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Objects;

/**
 * 处理{@link RSocketService}的{@link BeanPostProcessor}
 *
 * @author huangjianqin
 * @date 2021/3/28
 */
//最低优先级
@Order
public class RSocketServiceBeanPostProcessor implements BeanPostProcessor, BeanFactoryAware {
    /** 缺省group */
    private final String defaultGroup;
    /** 缺省version */
    private final String defaultVersion;

    private ConfigurableListableBeanFactory beanFactory;

    public RSocketServiceBeanPostProcessor() {
        this("", "");
    }

    public RSocketServiceBeanPostProcessor(String defaultGroup, String defaultVersion) {
        this.defaultGroup = defaultGroup;
        this.defaultVersion = defaultVersion;
    }

    @Override
    public void setBeanFactory(@Nonnull BeanFactory beanFactory) throws BeansException {
        this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
    }

    @Override
    public Object postProcessAfterInitialization(@Nonnull Object bean, @Nonnull String beanName) throws BeansException {
        parseRSocketServiceAnno(bean, beanName);
        return bean;
    }

    /**
     * 解析{@link RSocketService}注解
     */
    private void parseRSocketServiceAnno(Object bean, String beanName) {
        Class<?> beanClass = bean.getClass();
        RSocketService rsocketServiceAnno = AnnotationUtils.findAnnotation(beanClass, RSocketService.class);
        Map<String, Object> annoAttrsMap = null;
        if (Objects.nonNull(rsocketServiceAnno)) {
            annoAttrsMap = AnnotationUtils.getAnnotationAttributes(rsocketServiceAnno);
        } else {
            try {
                BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
                if (!(beanDefinition instanceof AnnotatedBeanDefinition)) {
                    return;
                }
                AnnotatedBeanDefinition annotatedBeanDefinition = (AnnotatedBeanDefinition) beanDefinition;
                //获取@RSocketService注解属性
                MethodMetadata factoryMethodMetadata = annotatedBeanDefinition.getFactoryMethodMetadata();
                if (Objects.isNull(factoryMethodMetadata)) {
                    return;
                }
                annoAttrsMap = factoryMethodMetadata.getAnnotationAttributes(RSocketService.class.getName());
            } catch (Exception e) {
                //ignore
                //有些bean没有bean definition, 比如spring.cloud.config-org.springframework.cloud.bootstrap.config.PropertySourceBootstrapProperties
            }
        }

        if (CollectionUtils.isEmpty(annoAttrsMap)) {
            return;
        }

        addProvider(bean, new AnnotationAttributes(annoAttrsMap));
    }

    private void addProvider(Object bean, AnnotationAttributes annoAttrs) {
        String service = annoAttrs.getString("name");
        Class<?> serviceInterfaceClass = annoAttrs.getClass("value");
        if (StringUtils.isBlank(service)) {
            //default
            service = serviceInterfaceClass.getName();
        }
        String group = annoAttrs.getString("group");
        if (StringUtils.isBlank(group)) {
            //default
            group = defaultGroup;
        }
        String version = annoAttrs.getString("version");
        if (StringUtils.isBlank(version)) {
            //default
            version = defaultVersion;
        }
        //注册
        LocalRSocketServiceRegistry.INSTANCE.addProvider(group, service, version, serviceInterfaceClass, bean, annoAttrs.getStringArray("tags"));
    }
}
