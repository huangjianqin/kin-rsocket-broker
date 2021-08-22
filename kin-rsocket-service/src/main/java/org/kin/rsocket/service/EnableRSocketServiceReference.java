package org.kin.rsocket.service;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 开启自动扫描并注册rsocket service reference bean
 *
 * @author huangjianqin
 * @date 2021/5/20
 */
@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(RSocketServiceReferenceRegistrar.class)
public @interface EnableRSocketServiceReference {
    /**
     * 指定扫描定义有{@link RSocketServiceReference}的服务接口的classpath
     */
    String[] basePackages() default {};

    /**
     * {@link RSocketServiceReference}集合
     * 注意, 该集合的所有注解必须配置{@link RSocketServiceReference#interfaceClass()}, 否则无效
     */
    RSocketServiceReference[] references() default {};
}
