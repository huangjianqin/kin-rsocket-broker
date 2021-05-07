package org.kin.rsocket.service;

import org.kin.rsocket.core.MetadataKeys;

/**
 * @author huangjianqin
 * @date 2021/5/2
 */
public interface ServiceMetadataKeys extends MetadataKeys {
    /** app服务权重, 值越大, 该app实例被路由的概率越大 */
    String WEIGHT = "weight";
}
