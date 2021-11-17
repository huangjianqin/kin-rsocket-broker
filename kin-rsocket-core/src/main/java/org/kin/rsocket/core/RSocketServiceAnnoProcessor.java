package org.kin.rsocket.core;

import org.kin.framework.utils.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;

import javax.annotation.Nonnull;

/**
 * 处理{@link RSocketService}的{@link BeanPostProcessor}
 *
 * @author huangjianqin
 * @date 2021/3/28
 */
public class RSocketServiceAnnoProcessor implements BeanPostProcessor {
    /** 缺省group */
    private final String defaultGroup;
    /** 缺省version */
    private final String defaultVersion;

    public RSocketServiceAnnoProcessor() {
        this("", "");
    }

    public RSocketServiceAnnoProcessor(String defaultGroup, String defaultVersion) {
        this.defaultGroup = defaultGroup;
        this.defaultVersion = defaultVersion;
    }

    @Override
    public Object postProcessBeforeInitialization(@Nonnull Object bean, @Nonnull String beanName) throws BeansException {
        scanRSocketServiceAnno(bean);
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(@Nonnull Object bean, @Nonnull String beanName) throws BeansException {
        return bean;
    }

    /**
     * 解析{@link RSocketService}注解
     */
    private void scanRSocketServiceAnno(Object bean) {
        Class<?> beanClass = bean.getClass();
        RSocketService rsocketServiceAnno = AnnotationUtils.findAnnotation(beanClass, RSocketService.class);
        if (rsocketServiceAnno != null) {
            String serviceName = rsocketServiceAnno.name();
            if (StringUtils.isBlank(serviceName)) {
                //default
                serviceName = rsocketServiceAnno.value().getCanonicalName();
            }
            String group = rsocketServiceAnno.group();
            if (StringUtils.isBlank(group)) {
                //default
                group = defaultGroup;
            }
            String version = rsocketServiceAnno.version();
            if (StringUtils.isBlank(version)) {
                //default
                version = defaultVersion;
            }
            //注册
            RSocketServiceRegistry.INSTANCE.addProvider(group, serviceName, version, rsocketServiceAnno.value(), bean, rsocketServiceAnno.tags());
        }
    }

}
