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
public class RSocketServiceAnnotationBeanPostProcessor implements BeanPostProcessor {
    /** 缺省group */
    private final String defaultGroup;
    /** 缺省version */
    private final String defaultVersion;

    public RSocketServiceAnnotationBeanPostProcessor() {
        this("", "");
    }

    public RSocketServiceAnnotationBeanPostProcessor(String defaultGroup, String defaultVersion) {
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
        if (rsocketServiceAnno == null) {
            return;
        }

        String service = rsocketServiceAnno.name();
        Class<?> serviceInterfaceClass = rsocketServiceAnno.value();
        if (StringUtils.isBlank(service)) {
            //default
            service = serviceInterfaceClass.getName();
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
        LocalRSocketServiceRegistry.INSTANCE.addProvider(group, service, version, serviceInterfaceClass, bean, rsocketServiceAnno.tags());
    }

}
