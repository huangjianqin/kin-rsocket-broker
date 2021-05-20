package org.kin.rsocket.service;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * @author huangjianqin
 * @date 2021/5/19
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(RSocketServiceReferenceRegistryRegistrar.class)
public @interface RSocketServiceReferenceRegistry {
    /**
     * {@link RSocketServiceReference}集合
     */
    RSocketServiceReference[] value();
}
