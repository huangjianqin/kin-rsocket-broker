package org.kin.rsocket.core;

/**
 * @author huangjianqin
 * @date 2021/8/18
 */
public interface MetricsNames {
    /** 计数器指标后缀 */
    String COUNT_SUFFIX = ".count";

    /** 数量上报指标后缀 */
    String NUM_SUFFIX = ".num";

    //-----------------------------------------------broker-----------------------------------------------
    /** 集群broker数量 */
    String CLUSTER_BROKER_NUM = "rsocket.cluster.broker" + NUM_SUFFIX;
    /** broker连接的app数量 */
    String BROKER_APPS_NUM = "rsocket.broker.apps" + NUM_SUFFIX;
    /** broker连接的对外提供服务app实例数量 */
    String BROKER_SERVICE_PROVIDER_NUM = "rsocket.broker.service.provider" + NUM_SUFFIX;
    /** broker暴露的服务数量 */
    String BROKER_SERVICE_NUM = "rsocket.broker.service" + NUM_SUFFIX;
    /** broker接受upstream服务请求次数 */
    String RSOCKET_REQUEST_COUNT = "rsocket.request".concat(COUNT_SUFFIX);


    //-----------------------------------------------rsocket service-----------------------------------------------
    /** 下游服务请求超时的次数 */
    String RSOCKET_TIMEOUT_ERROR_COUNT = "rsocket.timeout.error".concat(COUNT_SUFFIX);
}
