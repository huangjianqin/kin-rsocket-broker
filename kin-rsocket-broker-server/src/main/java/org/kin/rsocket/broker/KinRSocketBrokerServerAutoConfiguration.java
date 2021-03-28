package org.kin.rsocket.broker;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @author huangjianqin
 * @date 2021/2/15
 */
@Configuration
@EnableConfigurationProperties(KinRSocketBrokerServerProperties.class)
public class KinRSocketBrokerServerAutoConfiguration {

}
