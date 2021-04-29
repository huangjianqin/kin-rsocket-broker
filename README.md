# **kin-rsocket-broker**

Kin Rsocket Broker是一款基于RSocket协议的反应式对等通讯系统, 为通讯多方构建分布式的RPC, Pub/Sub, Streaming等通讯支持。

* 反应式: 无需担心线程模型、全异步化、流式背压支持、独特的对等通讯模式可适应各种内部网络环境和跨云混云的需求。
* 消息：面向消息通讯，服务路由、过滤、observability都非常简单。
* 交换系统：得益于RSocket, 完全分布式、异构系统整合简单，无论应用什么语言开发、部署在哪里，都可以相互通讯。

## **实现**
broker集群目前仅仅支持gossip, 通过maven配置kin-roscket-broker-gossip依赖实现

### **世界观**

一切皆服务, 包括broker, 一些内置功能是在broker实现rsocket service实现的

### **模块**
* **kin-rsocket-auth**: 权限校验模块
  * **kin-rsocket-auth-api**: 权限校验接口api模块
  * **kin-rsocket-auth-jwt-starter**: jwt权限校验实现
* **kin-roscket-broker**: rsocket broker实现
* **kin-rsocket-broker-conf-client-starter**: rsocket service conf client
* **kin-rsocket-broker-gateway-http-starter**: rsocket service http gateway
* **kin-rsocket-broker-gossip-starter**: gossip broker实现, 整合spring cloud
* **kin-rsocket-broker-registry-client-starter**: 以kin-rsocket-broker作为服务注册中心, 基于spring cloud discovery发现规则, 开发服务
* **kin-rsocket-broker-starter**: rsocket broker实现, 整合spring cloud
* **kin-rsocket-conf**: broker配置中心模块
  * **kin-rsocket-conf-api**: : broker配置中心接口api模块
  * **kin-rsocket-conf-h2-starter**: 基于h2文件系统实现的配置中心
  * **kin-rsocket-conf-memory-starter**: 基于内存实现的配置中心
* **kin-roscket-core**: rsocket核心功能, 实现一些共用的基础功能类
* **kin-roscket-example**: rsocket示例
  * **kin-roscket-example-broker**: rsocket broker示例
  * **kin-roscket-example-requester**: rsocket consumer示例
  * **kin-roscket-example-responder**: rsocket service示例
* **kin-roscket-service**: rsocket服务实现
* **kin-roscket-service-starter**: rsocket服务实现, 整合spring cloud