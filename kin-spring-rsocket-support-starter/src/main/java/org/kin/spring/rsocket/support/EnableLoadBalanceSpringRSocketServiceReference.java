package org.kin.spring.rsocket.support;

import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * @author huangjianqin
 * @date 2022/3/18
 */
@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import({SpringRSocketServiceDiscoveryMarkerConfiguration.class})
@EnableSpringRSocketServiceReference
public @interface EnableLoadBalanceSpringRSocketServiceReference {
    /**
     * 指定扫描定义有{@link SpringRSocketServiceReference}的服务接口的classpath
     */
    @AliasFor(annotation = EnableSpringRSocketServiceReference.class, attribute = "basePackages")
    String[] basePackages() default {};
}
