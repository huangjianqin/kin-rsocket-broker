package org.kin.rsocket.example.consumer;

import org.springframework.context.annotation.Configuration;

/**
 * @author huangjianqin
 * @date 2021/4/9
 */
@Configuration
//方式2. 通过@EnableRSocketServiceReference的字段references配置@RSocketServiceReference注册
public class RequesterConfiguration {
    /**
     * 方式1. 通过@Bean, 根据用户API手动注册
     */
//    @Bean
//    public UserService userService(@Autowired RSocketServiceRequester requester) {
//        return RSocketServiceReferenceBuilder
//                .requester(UserService.class)
//                .upstreamClusterManager(requester)
//                .build();
//    }
}
