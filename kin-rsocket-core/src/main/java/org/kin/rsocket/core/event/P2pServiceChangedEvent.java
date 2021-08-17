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
    private Set<String> p2pServiceGsvs;

    public static P2pServiceChangedEvent of(String appId, Set<String> p2pServiceGsvs) {
        P2pServiceChangedEvent inst = new P2pServiceChangedEvent();
        inst.appId = appId;
        inst.p2pServiceGsvs = p2pServiceGsvs;
        return inst;
    }

    //setter && getter
    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public Set<String> getP2pServiceGsvs() {
        return p2pServiceGsvs;
    }

    public void setP2pServiceGsvs(Set<String> p2pServiceGsvs) {
        this.p2pServiceGsvs = p2pServiceGsvs;
    }
}
