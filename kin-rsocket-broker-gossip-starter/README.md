# kin-rsocket-broker-gossip-starter

基于Gossip的`RSocketBrokerManager`实现. 依赖该模块, 然后`@EnableRSocketBroker`, 即启动基于Gossip发现集群其余broker的rsocket broker app.