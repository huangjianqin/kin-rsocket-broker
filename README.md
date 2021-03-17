# **kin-rsocket-broker**

Kin Rsocket Broker是一款基于RSocket协议的反应式对等通讯系统, 为通讯多方构建分布式的RPC, Pub/Sub, Streaming等通讯支持。

* 反应式: 无需担心线程模型、全异步化、流式背压支持、独特的对等通讯模式可适应各种内部网络环境和跨云混云的需求。
* 消息：面向消息通讯，服务路由、过滤、observability都非常简单。
* 交换系统：得益于RSocket, 完全分布式、异构系统整合简单，无论应用什么语言开发、部署在哪里，都可以相互通讯。

## **实现**

### **模块**

* **kin-roscket-broker-server**: rsocket broker实现
* **kin-roscket-broker-core**: rsocket核心功能, 实现一些共用的基础功能类
* **kin-roscket-broker-service**: rsocket服务实现
* **kin-roscket-broker-example**: rsocket服务例子