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
    private Set<String> p2pServiceIds;

    public static P2pServiceChangedEvent of(String appId, Set<String> p2pServiceIds) {
        P2pServiceChangedEvent event = new P2pServiceChangedEvent();
        event.appId = appId;
        event.p2pServiceIds = p2pServiceIds;
        return event;
    }

    //setter && getter
    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public Set<String> getP2pServiceIds() {
        return p2pServiceIds;
    }

    public void setP2pServiceIds(Set<String> p2pServiceIds) {
        this.p2pServiceIds = p2pServiceIds;
    }
}
