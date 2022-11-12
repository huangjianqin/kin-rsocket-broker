package org.kin.rsocket.gateway.grpc;

import org.kin.rsocket.service.boot.EnableRSocketService;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 开启自动扫描grpc service stub并且将grpc call路由到rsocket broker
 *
 * @author huangjianqin
 * @date 2022/1/11
 */
@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(GrpcServiceRSocketImplementationRegistrar.class)
@EnableRSocketService
public @interface EnableGrpcServiceRSocketImpl {
    /**
     * 需要将grpc call路由到rsocket broker的grpc service stub package
     */
    String[] basePackages() default {};
}
