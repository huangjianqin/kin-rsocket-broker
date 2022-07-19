package org.kin.rsocket.cloud.function;

import java.lang.annotation.*;

/**
 * @author huangjianqin
 * @date 2022/7/19
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface RSocketFunction {
    /**
     * service group
     *
     * @return group
     */
    String group() default "";

    /**
     * service version
     *
     * @return version
     */
    String version() default "";

    /**
     * service tags
     *
     * @return labels
     */
    String[] tags() default {};
}
