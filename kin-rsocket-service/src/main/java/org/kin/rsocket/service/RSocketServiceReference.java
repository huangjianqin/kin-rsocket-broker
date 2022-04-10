package org.kin.rsocket.service;

import org.kin.rsocket.core.RSocketMimeType;

import java.lang.annotation.*;

/**
 * 通过注解方法创建rsocket service reference
 * 注意: 最终rsocket service reference的bean name是其service name, 所以注意不要重复, 不然会启动错误
 * <p>
 * 4种使用方式:
 * 1. org.kin.rsocket.springcloud.service.EnableRSocketService和org.kin.rsocket.springcloud.gateway.grpc.EnableRSocketGrpcServiceReference的references()字段
 * 2. 注解在接口上
 * 3. 注解在字段变量上
 * 4. 使用{@link RSocketServiceReferenceBuilder}创建rsocket service reference, 并以{@link org.springframework.context.annotation.Bean}方式注解
 *
 * @author huangjianqin
 * @date 2021/5/19
 */
@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RSocketServiceReference {
    /**
     * 目前仅仅只有org.kin.rsocket.springcloud.service.EnableRSocketService和org.kin.rsocket.springcloud.gateway.grpc.EnableRSocketGrpcServiceReference才会使用到
     * 其余三种方式取字段变量类型
     *
     * @return service interface
     */
    Class<?> interfaceClass() default Void.class;

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
    RSocketMimeType encodingType() default RSocketMimeType.JSON;

    /** accept 编码类型 */
    RSocketMimeType[] acceptEncodingTypes() default {RSocketMimeType.JSON};

    /** consumer 是否开启p2p直连模式 */
    boolean p2p() default false;
}
