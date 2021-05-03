package org.kin.rsocket.core;

import java.lang.annotation.*;

/**
 * @author huangjianqin
 * @date 2021/5/3
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.PARAMETER})
@Documented
public @interface Desc {
    String value() default "";
}
