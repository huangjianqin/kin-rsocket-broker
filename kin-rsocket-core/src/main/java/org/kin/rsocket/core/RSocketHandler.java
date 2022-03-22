package org.kin.rsocket.core;

import java.lang.annotation.*;

/**
 * 给rsocket service handler(method)提供额外自定义参数的注解
 *
 * @author huangjianqin
 * @date 2021/3/26
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RSocketHandler {
    /** handler name, 默认=method name */
    String value() default "";

    /** 参数编码 mime type, 默认{@link RSocketMimeType#JSON} */
    RSocketMimeType paramEncoding() default RSocketMimeType.JSON;

    /** 返回值编码 mime type, 默认{@link RSocketMimeType#JSON} */
    RSocketMimeType[] resultEncodings() default {RSocketMimeType.JSON};

    /** endpoint, such as id:xxxx,  ip:192.168.1.2 */
    String endpoint() default "";

    /** sticky session */
    boolean sticky() default false;

    /**
     * call timeout, 默认3s
     */
    int callTimeout() default 3000;
}
