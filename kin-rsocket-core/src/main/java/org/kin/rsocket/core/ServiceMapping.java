package org.kin.rsocket.core;

import java.lang.annotation.*;

/**
 * 标识接口(及其方法)属性注解
 *
 * @author huangjianqin
 * @date 2021/3/26
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ServiceMapping {
    /** handler name, 默认=method name */
    String value() default "";

    /** 所属组 */
    String group() default "kin";

    /** 版本号 */
    String version() default "0.1.0.0";

    /** 参数编码 MIME Type, 默认{@link RSocketMimeType#Json} */
    String paramEncoding() default "Json";

    /** 返回值编码 MIME Type, 默认{@link RSocketMimeType#Json} */
    String resultEncoding() default "Json";

    /** endpoint, such as id:xxxx,  ip:192.168.1.2 */
    String endpoint() default "";

    /** sticky session */
    boolean sticky() default false;
}
