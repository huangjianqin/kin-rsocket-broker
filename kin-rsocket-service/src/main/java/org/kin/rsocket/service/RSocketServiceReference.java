package org.kin.rsocket.service;

import org.kin.rsocket.core.RSocketMimeType;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * @author huangjianqin
 * @date 2021/5/19
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(RSocketServiceReferenceRegistrar.class)
public @interface RSocketServiceReference {
    /**
     * service interface
     *
     * @return service interface
     */
    Class<?> value();

    /**
     * service name
     *
     * @return service name
     */
    String name() default "";

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
     * call timeout, 默认3s
     */
    int callTimeout() default 3000;

    /**
     * endpoint
     * 形式:
     * 1. id:XX
     * 2. uuid:XX
     * 3. ip:XX
     */
    String endpoint() default "";

    /**
     * sticky session
     * 相当于固定session, 指定service首次请求后, 后面请求都是route到该service instance
     * 如果该service instance失效, 重新选择一个sticky service instance
     * 目前仅仅在service mesh校验通过下才允许mark sticky service instance
     */
    boolean sticky() default false;

    /** 数据编码类型 */
    RSocketMimeType encodingType() default RSocketMimeType.Java_Object;

    /** accept 编码类型 */
    RSocketMimeType[] acceptEncodingTypes() default {RSocketMimeType.Java_Object};
}
