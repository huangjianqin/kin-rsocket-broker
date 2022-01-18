# kin-rsocket-gateway-http-starter

基于`spring-webflux`, 拦截http请求并解析其参数, 然后向rsocket broker发送请求.

## 拦截url

* `/api/{service}/{method}`, 参数包括group, service, version, handler, auth以及ByteBuf body