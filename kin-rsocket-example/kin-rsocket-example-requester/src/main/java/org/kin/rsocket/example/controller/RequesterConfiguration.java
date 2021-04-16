package org.kin.rsocket.example.controller;

import org.kin.rsocket.example.UserService;
import org.kin.rsocket.service.ServiceReferenceBuilder;
import org.kin.rsocket.service.UpstreamClusterManager;
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
    public UserService userService(@Autowired UpstreamClusterManager upstreamClusterManager) {
        return ServiceReferenceBuilder
                .requester(UserService.class)
                //.sticky(true)
                .upstreamClusterManager(upstreamClusterManager)
                //.endpoint("ip:192.168.1.2") //for testing
                .build();
    }
}
