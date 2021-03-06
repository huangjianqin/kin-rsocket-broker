package org.kin.rsocket.example.consumer;

import org.kin.rsocket.service.EnableRSocketServiceReference;
import org.springframework.context.annotation.Configuration;

/**
 * @author huangjianqin
 * @date 2021/4/9
 */
@Configuration
//方式2. 通过@EnableRSocketServiceReference的字段references配置@RSocketServiceReference注册
@EnableRSocketServiceReference
public class RequesterConfiguration {
    /**
     * 方式1. 通过@Bean, 根据用户API手动注册
     */
//    @Bean
//    public UserService userService(@Autowired RSocketServiceConnector connector) {
//        return RSocketServiceReferenceBuilder
//                .requester(UserService.class)
//                .upstreamClusterManager(connector)
//                .build();
//    }
}
