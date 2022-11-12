# kin-rsocket-service-spring-support-starter
相当于`spring-messaging`的工具类

* `@EnableRSocketServiceReference`, 提供自动扫描并构建rsocket service reference
    * 需要提供`org.springframework.messaging.rsocket.RSocketRequester` bean, 默认构建,
      但broker模式下需开发者手动指定broker元数据信息并构建`org.springframework.messaging.rsocket.RSocketRequester` bean
* `@EnableLoadBalanceRSocketServiceReference`, 相当于`@RSocketServiceReference`的增强版本.
  基于`ReactiveDiscoveryClient`发现并缓存naming service上注册的rsocket service实例,
  并创建支持负载均衡的`org.springframework.messaging.rsocket.RSocketRequester`, 从而实现支持负载均衡的rsocket service
  reference.
    * 引入spring discovery client service
    * naming service上注册的rsocket service实例, 需要配置元数据
        * `rsocketSchemaPorts`: rsocket service传输层信息, 格式是传输层协议:端口, 比如`tcp:9998`. 支持配置多个,
          但目前实现只会取第一个.
        * `rsocketWsPath`: 如果传输层式websocket时, 选择性配置. 格式是websocket path内容, 比如`/rsocket`
    * 基于应用名发现rsocket service, 故说明实现如何识别发现提取应用名.
      假设服务名为`org.kin.spring.rsocket.example.UserService-{version}`,
      则应用名为`org.kin.spring.rsocket.example`. 如果rsocket service是基于broker模式搭建, 而可以这样子定义服务名, 如
      `{broker app name}:org.kin.spring.rsocket.example.UserService-{version}`, 则我们直接认为`{broker app name}`为应用名.
      注意,
      这里面的broker也是作为服务注册到naming service上.
* `@RSocketService`, `@MessageMapping`的替身, 用于定义标识rsocket service
* `@RSocketHandler`, `@MessageMapping`的替身, 用于定义标识rsocket handler

目前`@RSocketServiceReference`支持3种使用方式:

* 使用`@Bean`+`RSocketServiceReferenceBuilder`构建rsocket service reference

```java

@Configuration
public class RequesterConfiguration {
    @Bean
    public UserService userService(@Autowired RSocketRequester rsocketRequester) {
        return RSocketServiceReferenceBuilder
                .reference(rsocketRequester, UserService.class)
                .build();
    }
}
```

* 使用`@Bean`+`@RSocketServiceReference`构建rsocket service reference

```java

@Configuration
public class RequesterConfiguration {
    @Bean
    @RSocketServiceReference(interfaceClass = UserService.class, appName = "XXXX")
    public RSocketServiceReferenceFactoryBean<UserService> userService() {
        return new RSocketServiceReferenceFactoryBean<>();
    }
}
```

* 使用`@RSocketServiceReference`注解在字段变量上构建rsocket service reference

```java

@RestController
public class UserController {
    @RSocketServiceReference(appName = "XXXX")
    private UserService userService;
}
```