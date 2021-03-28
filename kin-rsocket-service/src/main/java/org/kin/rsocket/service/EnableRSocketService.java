package org.kin.rsocket.service;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * @author huangjianqin
 * @date 2021/3/28
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({RSocketAutoConfiguration.class, RSocketBinderAutoConfiguration.class})
public @interface EnableRSocketService {
}
