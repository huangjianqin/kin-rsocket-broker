package org.kin.rsocket.core.event;

import java.util.List;

/**
 * 服务注册实例变化事件
 *
 * @author huangjianqin
 * @date 2021/8/13
 */
public class ServiceInstanceChangedEvent implements CloudEventSupport {
    private static final long serialVersionUID = -3672208076284316225L;
    /** group */
    private String group;
    /** service */
    private String service;
    /** version */
    private String version;
    /** 服务实例暴露的rsocket url */
    private List<String> uris;

    public static ServiceInstanceChangedEvent of(String group, String service, String version, List<String> uris) {
        ServiceInstanceChangedEvent inst = new ServiceInstanceChangedEvent();
        inst.group = group;
        inst.service = service;
        inst.version = version;
        inst.uris = uris;
        return inst;
    }

    //setter && getter
    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
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
