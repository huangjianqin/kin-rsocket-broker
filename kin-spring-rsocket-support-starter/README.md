# kin-spring-rsocket-support-starter

相当于`spring-messaging`的工具类

* `@SpringRSocketServiceReference`, 提供自动扫描rsocket service interface, 并使用代理拦截其接口方法.
    * 需要使用者定义`org.springframework.messaging.rsocket.RSocketRequester` bean
* `@EnableLoadBalanceSpringRSocketServiceReference`, 相当于`@SpringRSocketServiceReference`的增强版本.
  基于`ReactiveDiscoveryClient`发现并缓存naming service上注册的rsocket service实例,
  并创建支持负载均衡的`org.springframework.messaging.rsocket.RSocketRequester`, 从而实现支持负载均衡的rsocket service reference.
    * 引入spring discovery client service
    * naming service上注册的rsocket service实例, 需要配置元数据
        * `rsocketSchemaPorts`: rsocket service传输层信息, 格式是传输层协议:端口, 比如`tcp:9998`. 支持配置多个, 但目前实现只会取第一个.
        * `rsocketWsPath`: 如果传输层式websocket时, 选择性配置. 格式是websocket path内容, 比如`/rsocket`
    * 基于应用名发现rsocket service, 故说明实现如何识别发现提取应用名. 假设服务名为`org.kin.spring.rsocket.example.UserService-{version}`,
      则应用名为`org.kin.spring.rsocket.example`. 如果rsocket service是基于broker模式搭建, 而可以这样子定义服务名, 如
      `{broker app name}:org.kin.spring.rsocket.example.UserService-{version}`, 则我们直接认为`{broker app name}`为应用名