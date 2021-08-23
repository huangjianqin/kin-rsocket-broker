package org.kin.spring.rsocket.support;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 开启自动扫描并注册spring rsocket service reference bean
 *
 * @author huangjianqin
 * @date 2021/5/20
 */
@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(SpringRSocketServiceReferenceRegistrar.class)
public @interface EnableSpringRSocketServiceReference {
    /**
     * 指定扫描定义有{@link SpringRSocketServiceReference}的服务接口的classpath
     */
    String[] basePackages() default {};
}
