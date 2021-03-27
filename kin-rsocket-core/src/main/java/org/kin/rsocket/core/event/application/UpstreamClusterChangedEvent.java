package org.kin.rsocket.core.event.application;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.kin.rsocket.core.event.CloudEventSupport;
import org.kin.rsocket.core.utils.Symbols;

import java.util.List;

/**
 * rsocket service变化
 * 由broker广播给所有application处理
 *
 * @author huangjianqin
 * @date 2021/3/27
 */
public class UpstreamClusterChangedEvent implements CloudEventSupport<UpstreamClusterChangedEvent> {
    /** 服务接口名 */
    private String interfaceName;
    /** 服务组 */
    private String group;
    /** 版本 */
    private String version;
    /** 服务地址 */
    private List<String> uris;

    public static UpstreamClusterChangedEvent of(String group, String interfaceName, String version, List<String> uris) {
        UpstreamClusterChangedEvent event = new UpstreamClusterChangedEvent();
        event.interfaceName = interfaceName;
        event.group = group;
        event.version = version;
        event.uris = uris;
        return event;
    }

    /**
     * 是否broker服务
     */
    @JsonIgnore
    public boolean isBrokerCluster() {
        return Symbols.BROKER.equals(interfaceName);
    }

    //setter && getter
    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<String> getUris() {
        return uris;
    }

    public void setUris(List<String> uris) {
        this.uris = uris;
    }
}
