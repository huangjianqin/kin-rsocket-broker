package org.kin.rsocket.example.consumer;

import org.kin.rsocket.example.UserService;
import org.kin.rsocket.service.RSocketServiceConnector;
import org.kin.rsocket.service.ServiceReferenceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author huangjianqin
 * @date 2021/4/9
 */
@Configuration
public class RequesterConfiguration {
    @Bean
    public UserService userService(@Autowired RSocketServiceConnector connector) {
        return ServiceReferenceBuilder
                .requester(UserService.class)
                .upstreamClusterManager(connector)
                .build();
    }
}
