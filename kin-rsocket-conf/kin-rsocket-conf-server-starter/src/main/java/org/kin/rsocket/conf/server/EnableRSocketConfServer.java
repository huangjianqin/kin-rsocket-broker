package org.kin.rsocket.conf.server;

import org.kin.rsocket.service.spring.EnableRSocketService;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * @author huangjianqin
 * @date 2022/4/9
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@EnableRSocketService
@Import({RSocketConfServerMarkerConfiguration.class})
public @interface EnableRSocketConfServer {
}
