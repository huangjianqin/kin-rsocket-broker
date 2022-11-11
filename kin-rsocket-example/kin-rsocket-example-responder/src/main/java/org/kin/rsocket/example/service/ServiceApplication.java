package org.kin.rsocket.example.service;

import org.kin.rsocket.service.boot.EnableRSocketService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author huangjianqin
 * @date 2021/4/9
 */
@EnableRSocketService
@SpringBootApplication
public class ServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServiceApplication.class, args);
    }

//    @Bean
//    @RSocketService(UserService.class)
//    public UserService userService(){
//        return new UserServiceImpl();
//    }
}
