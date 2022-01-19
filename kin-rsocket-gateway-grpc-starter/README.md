# kin-rsocket-gateway-grpc-starter

使用注意:

* requester和responder端的请求和返回都得是protobuf生成的message

实现细节:

* 因为`GRpcAutoConfiguration`带有`@ConditionalOnBean(annotation = GRpcService.class)`, 所以
  `grpc-spring-boot-starter`的使用前提是存在一个bean是带有`@GRpcService`注解的.
  `GRpcAutoConfiguration`的加载是在`ConfigurationClassPostProcessor#postProcessBeanDefinitionRegistry()`完成.
  如果在该阶段没有任何带`@GRpcService`的扫描到, 则无法启动grpc server, 并注册grpc service. 目前的解决方案是 内置一个`HealthGrpc.HealthImplBase`grpc
  service实现, 并提供修改health状态api, 以修改当前grpc gateway health state.