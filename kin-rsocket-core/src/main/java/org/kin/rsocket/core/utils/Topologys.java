package org.kin.rsocket.core.utils;

/**
 * @author huangjianqin
 * @date 2021/3/28
 */
public interface Topologys {
    /** 所有的应用都可以访问broker实例的内部IP地址，Gossip广播的broker IP地址都可以被应用访问，这个也是最简单的。 */
    String INTRANET = "intranet";
    /**
     * 应用从互联网接入，这个时候broker实例要以外部域名对外提供接入，这个时候需要broker包含对外的域名，
     * 你需要给每一个broker实例设置外部域名，如果是容器环境，你可以设置环境变量"RSOCKET_BROKER_EXTERNAL_DOMAIN"
     */
    String INTERNET = "internet";
}
