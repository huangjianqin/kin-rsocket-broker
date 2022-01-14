package org.kin.rsocket.core;

import org.kin.rsocket.core.domain.RSocketServiceInfo;

/**
 * 服务注册表
 *
 * @author huangjianqin
 * @date 2021/3/27
 */
public interface RSocketServiceInfoSupport {
    /**
     * 用于后台访问服务接口具体信息
     */
    RSocketServiceInfo getReactiveServiceInfoByName(String service);
}
