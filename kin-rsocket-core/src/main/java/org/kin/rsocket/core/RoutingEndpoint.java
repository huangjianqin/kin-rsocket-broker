package org.kin.rsocket.core;

import java.util.List;

/**
 * endpoint配置
 *
 * @author huangjianqin
 * @date 2021/3/27
 */
public class RoutingEndpoint {
    /** group: region, datacenter, virtual group in datacenter */
    private String group;
    /** service name */
    private String service;
    /** version */
    private String version;
    /** endpoint uri list */
    private List<String> uris;

    public static RoutingEndpoint of(String group,
                                     String service,
                                     String version,
                                     List<String> uris) {
        RoutingEndpoint inst = new RoutingEndpoint();
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
