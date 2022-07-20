package org.kin.rsocket.example.requester;

import org.kin.rsocket.example.UserService;
import org.kin.rsocket.service.RSocketServiceReference;
import org.kin.rsocket.service.spring.RSocketServiceReferenceFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author huangjianqin
 * @date 2021/4/9
 */
@Configuration
public class RequesterConfiguration {
//    /**
//     * 方式1. 通过{@link Bean}+{@link RSocketServiceReferenceBuilder}构建rsocket service reference
//     */
//    @Bean
//    public UserService userService(@Autowired RSocketServiceRequester requester) {
//        return RSocketServiceReferenceBuilder
//                .requester(UserService.class)
//                .upstreamClusterManager(requester)
//                .build();
//    }

    /**
     * 方式2. 通过{@link Bean}+{@link RSocketServiceReference}构建rsocket service reference
     */
    @Bean
    @RSocketServiceReference(interfaceClass = UserService.class)
    public RSocketServiceReferenceFactoryBean<UserService> userService() {
        return new RSocketServiceReferenceFactoryBean<>();
    }
}
