package org.kin.spring.rsocket.support;

import org.springframework.core.annotation.AliasFor;
import org.springframework.messaging.handler.annotation.MessageMapping;

import java.lang.annotation.*;

/**
 * {@link MessageMapping}替换, 用于标识rsocket service handler
 *
 * @author huangjianqin
 * @date 2022/3/18
 */
@Documented
@Inherited
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@MessageMapping
public @interface SpringRSocketHandler {
    @AliasFor(annotation = MessageMapping.class, attribute = "value")
    String[] value() default {};
}
