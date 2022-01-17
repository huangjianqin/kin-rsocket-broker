# kin-rsocket-gateway-grpc-starter

使用注意:

* requester和responder端的请求和返回都得是protobuf生成的message

实现细节:

* `grpc-spring-boot-starter`的使用前提是存在一个bean的bean type是带有`@GRpcService`注解的. 所以, 本grpc gateway自定义了health service,
  带有`@GRpcService`. 这样子,
  `GRpcAutoConfiguration`的`@ConditionalOnBean(annotation = GRpcService.class)`才能满足, 进而, 加载`GRpcAutoConfiguration`. 然后,
  我们代码通过bytebuddy实现grpc service stub子类, 其带有
  `@GRpcService`, 方法逻辑则是将请求封装, 然后向RSocket Broker发送请求.