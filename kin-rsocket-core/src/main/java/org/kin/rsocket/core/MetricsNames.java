package org.kin.rsocket.core;

/**
 * @author huangjianqin
 * @date 2021/8/18
 */
public interface MetricsNames {
    /** 监控变量后缀 */
    String COUNT_SUFFIX = ".count";

    //-----------------------------------------------broker-----------------------------------------------
    /** 集群broker数量 */
    String CLUSTER_BROKER_COUNT = "cluster.broker".concat(COUNT_SUFFIX);
    /** broker连接的app数量 */
    String BROKER_APPS_COUNT = "broker.apps".concat(COUNT_SUFFIX);
    /** broker连接的对外提供服务app实例数量 */
    String BROKER_SERVICE_PROVIDER_COUNT = "broker.service.provider".concat(COUNT_SUFFIX);
    /** broker暴露的服务数量 */
    String BROKER_SERVICE_COUNT = "broker.service".concat(COUNT_SUFFIX);
    /** broker接受upstream服务请求次数 */
    String RSOCKET_REQUEST_COUNT = "rsocket.request".concat(COUNT_SUFFIX);


    //-----------------------------------------------rsocket service-----------------------------------------------
    /** 下游服务请求超时的次数 */
    String RSOCKET_TIMEOUT_ERROR_COUNT = "rsocket.timeout.error".concat(COUNT_SUFFIX);
}
