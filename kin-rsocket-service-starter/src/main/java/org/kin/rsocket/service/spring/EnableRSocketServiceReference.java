package org.kin.rsocket.service.spring;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 开启自动扫描并注册rsocket service reference bean
 *
 * @author huangjianqin
 * @date 2021/5/20
 */
@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import({RSocketServiceReferenceFieldPostProcessor.class})
@EnableRSocketService
public @interface EnableRSocketServiceReference {
}
