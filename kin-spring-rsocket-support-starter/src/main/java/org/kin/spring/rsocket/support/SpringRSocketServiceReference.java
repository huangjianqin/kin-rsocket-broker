package org.kin.spring.rsocket.support;

import java.lang.annotation.*;

/**
 * 通过注解方法创建spring rsocket service reference
 *
 * @author huangjianqin
 * @date 2021/5/19
 */
@Target(ElementType.TYPE)
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
}