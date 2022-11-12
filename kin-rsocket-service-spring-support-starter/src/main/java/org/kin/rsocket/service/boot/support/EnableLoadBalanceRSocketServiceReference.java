package org.kin.rsocket.service.boot.support;

import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 基于spring {@link ReactiveDiscoveryClient}发现naming service注册的rsocket service实例, 并构建rsocket service reference
 *
 * @author huangjianqin
 * @date 2022/3/18
 */
@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import({RSocketServiceDiscoveryMarkerConfiguration.class})
@EnableRSocketServiceReference
public @interface EnableLoadBalanceRSocketServiceReference {
}
