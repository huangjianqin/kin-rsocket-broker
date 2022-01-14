package org.kin.rsocket.springcloud.gateway.grpc;

import io.grpc.BindableService;
import org.kin.rsocket.service.RSocketServiceReference;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 开启自动扫描grpc service interface并且将grpc call路由到rsocket broker
 *
 * @author huangjianqin
 * @date 2022/1/11
 */
@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(RSocketGrpcServiceReferenceRegistrar.class)
public @interface EnableRSocketGrpcServiceReference {
    /**
     * 需要将grpc call路由到rsocket broker的grpc service interface package
     */
    String[] basePackages() default {};

    /**
     * 对代理grpc service的rsocket service reference进行更多自定义配置
     * <p>
     * !!! 注意{@link #basePackages()}不能包含指定配置的grpc service interface
     * !!! 注意{@link RSocketServiceReference#interfaceClass()}必须实现了{@link BindableService}
     */
    RSocketServiceReference[] references() default {};
}
