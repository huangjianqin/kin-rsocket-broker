# kin-rsocket-cloud-function-starter

基于`spring-cloud-starter-function-webflux`, 整合serviceless和rsocket service, 将`function`以rsocket service方式向rsocket
broker注册.

使用`@RSocketFunction`注解标识function bean, 同时function name的命名规则是` service name + '.' + handler`

```java

@SpringBootApplication
@EnableRSocketService
public class RSocketFunctionApplication {
    private static final List<User> USERS = Arrays.asList(
            User.of("A", 1),
            User.of("B", 2),
            User.of("C", 3),
            User.of("D", 4),
            User.of("E", 5)
    );

    public static void main(String[] args) {
        SpringApplication.run(RSocketFunctionApplication.class, args);
    }

    @RSocketFunction()
    @Bean("org.kin.rsocket.example.UserService.findAll")
    public Supplier<Flux<User>> org$Kin$RSocket$Example$UserService$findAll() {
        return () -> Flux.fromIterable(USERS);
    }
}
```