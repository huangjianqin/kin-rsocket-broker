package org.kin.rsocket.spingcloud.conf.client;

import org.kin.rsocket.core.MetadataKeys;

/**
 * @author huangjianqin
 * @date 2021/5/2
 */
public interface ConfigMetadataKeys extends MetadataKeys {
    /** 标识app使用了配置中心 */
    String CONF = Base + "config";
    /** 自动刷新配置标识 */
    String AUTO_REFRESH = Base + "auto-refresh";
}
