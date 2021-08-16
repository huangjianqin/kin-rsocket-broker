package org.kin.rsocket.core.event;

import java.util.Set;

/**
 * 下游开启p2p服务的gsv改变
 *
 * @author huangjianqin
 * @date 2021/8/17
 */
public class P2pServiceChangedEvent implements CloudEventSupport {
    private static final long serialVersionUID = -7564852652744842864L;

    /** application id */
    private String appId;
    /** 开启p2p服务的gsv */
    private Set<String> p2pServices;

    public static P2pServiceChangedEvent of(String appId, Set<String> p2pServices) {
        P2pServiceChangedEvent inst = new P2pServiceChangedEvent();
        inst.appId = appId;
        inst.p2pServices = p2pServices;
        return inst;
    }

    //setter && getter
    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public Set<String> getP2pServices() {
        return p2pServices;
    }

    public void setP2pServices(Set<String> p2pServices) {
        this.p2pServices = p2pServices;
    }
}
