package org.kin.rsocket.service.boot.support;

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
@Import({RSocketServiceReferenceBeanPostProcessor.class})
public @interface EnableRSocketServiceReference {
}
