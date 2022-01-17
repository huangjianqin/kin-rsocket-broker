# Spring-RSocket-Support

相当于`spring-messaging`的工具类, 提供自动扫描带特定注解`SpringRSocketServiceReference`的rsocket service interface, 并使用代理拦截其接口方法, 使其内部实现使用
`org.springframework.messaging.rsocket.RSocketRequester`来向remote rsocket service发送请求.