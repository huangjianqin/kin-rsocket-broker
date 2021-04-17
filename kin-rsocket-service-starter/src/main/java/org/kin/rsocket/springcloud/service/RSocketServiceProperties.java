package org.kin.rsocket.springcloud.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author huangjianqin
 * @date 2021/4/17
 */
@ConfigurationProperties(prefix = "kin.rsocket")
public class RSocketServiceProperties extends org.kin.rsocket.service.RSocketServiceProperties {
}
