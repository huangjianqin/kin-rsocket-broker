# kin-rsocket-gateway-grpc-starter

使用注意:

* requester和responder端的请求和返回都得是protobuf生成的message

实现细节:

* 因为`GRpcAutoConfiguration`带有`@ConditionalOnBean(annotation = GRpcService.class)`, 所以
  `grpc-spring-boot-starter`的使用前提是存在一个bean是带有`@GRpcService`注解的. 这意味处理`GRpcAutoConfiguration`前, bean
  factory必须拥有带有`@GRpcService`注解的bean definition. 这里有两个处理方案, 1是开发者带`@SpringBootApplication`的spring main class的
  classpath拥有带`@GRpcService`实现, 2是框架本身内置带`@GRpcService`实现, 并通过auto configuration在`GRpcAutoConfiguration`
  前注册带`@GRpcService`的bean 目前采用的方案2, 即内置一个`HealthGrpc.HealthImplBase`grpc service实现, 并提供修改health状态api, 以修改当前grpc gateway
  health state.

目前支持两种方式构建grpc service rsocket implementation:

* 通过`@EnableRSocketGrpcServiceReference.basePackages()`扫明指定classpath, 并构建grpc service rsocket implementation

```java

@SpringBootApplication
@EnableRSocketGrpcServiceReference(basePackages = "org.kin.rsocket.example")
public class GrpcGatewayApplication implements WebFluxConfigurer {
    public static void main(String[] args) {
        SpringApplication.run(GrpcGatewayApplication.class, args);
    }
}
```

* 通过`@Bean`+`RSocketGrpcServiceReferenceFactoryBean`, 构建grpc service rsocket implementation

```java

@SpringBootApplication
@EnableRSocketGrpcServiceReference
public class GrpcGatewayApplication implements WebFluxConfigurer {
    public static void main(String[] args) {
        SpringApplication.run(GrpcGatewayApplication.class, args);
    }

    @Bean
    public RSocketGrpcServiceReferenceFactoryBean<ReactorUserServiceGrpc.UserServiceImplBase> userService() {
        return new RSocketGrpcServiceReferenceFactoryBean<>(ReactorUserServiceGrpc.UserServiceImplBase.class);
    }
}
```