package org.kin.spring.rsocket.support;

import java.lang.annotation.*;

/**
 * 通过注解方法创建spring rsocket service reference
 *
 * @author huangjianqin
 * @date 2021/5/19
 */
@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SpringRSocketServiceReference {
    /**
     * 服务名
     */
    String service() default "";

    /**
     * call timeout, 默认3s
     */
    int callTimeout() default 3000;

    /**
     * naming service上注册的rsocket service application name
     * 目前字段仅仅在使用{@link EnableLoadBalanceSpringRSocketServiceReference}前提下有效
     *
     * @see EnableLoadBalanceSpringRSocketServiceReference
     */
    String appName() default "";
}
