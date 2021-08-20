package org.kin.rsocket.conf;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.context.annotation.Configuration;

import java.lang.annotation.*;

/**
 * @author huangjianqin
 * @date 2021/8/21
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Configuration
//配置中心必须在broker auto configuration前加载
@AutoConfigureBefore(name = "org.kin.rsocket.broker.RSocketBrokerAutoConfiguration")
public @interface RSocketConfDiamondConfiguration {
}
