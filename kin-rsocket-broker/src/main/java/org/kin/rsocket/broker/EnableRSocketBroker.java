package org.kin.rsocket.broker;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * @author huangjianqin
 * @date 2021/4/29
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import({RSocketBrokerConfiguration.class})
public @interface EnableRSocketBroker {
}
