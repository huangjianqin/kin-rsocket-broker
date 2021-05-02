package org.kin.rsocket.broker;

import org.kin.rsocket.core.MetadataKeys;

/**
 * @author huangjianqin
 * @date 2021/5/2
 */
public interface BrokerMetadataKeys extends MetadataKeys {
    /** 标识app是broker */
    String BROKER = "broker";
}
