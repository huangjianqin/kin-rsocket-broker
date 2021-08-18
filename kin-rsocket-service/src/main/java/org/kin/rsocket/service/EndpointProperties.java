package org.kin.rsocket.service;

import java.util.Arrays;
import java.util.List;

/**
 * endpoint配置, 主要用于直连测试
 *
 * @author huangjianqin
 * @date 2021/3/27
 */
public class EndpointProperties {
    /** group: region, datacenter, virtual group in datacenter */
    private String group;
    /** service name */
    private String service;
    /** version */
    private String version;
    /** endpoint uri list */
    private List<String> uris;

    public static EndpointProperties of(String group,
                                        String service,
                                        String version,
                                        List<String> uris) {
        EndpointProperties inst = new EndpointProperties();
        inst.group = group;
        inst.service = service;
        inst.version = version;
        inst.uris = uris;
        return inst;
    }

    public static EndpointProperties of(String group,
                                        String service,
                                        String version,
                                        String... uris) {
        return of(group, service, version, Arrays.asList(uris));
    }

    public static EndpointProperties of(String service, List<String> uris) {
        return of("", service, "", uris);
    }

    public static EndpointProperties of(String service, String... uris) {
        return of("", service, "", Arrays.asList(uris));
    }

    //----------------------------------------------------------------------------------------------------------------------------------------
    public static Builder builder() {
        return new Builder();
    }

    /** builder **/
    public static class Builder {
        private EndpointProperties endpointProperties = new EndpointProperties();

        public Builder group(String group) {
            endpointProperties.group = group;
            return this;
        }

        public Builder service(String service) {
            endpointProperties.service = service;
            return this;
        }

        public Builder version(String version) {
            endpointProperties.version = version;
            return this;
        }

        public Builder uris(List<String> uris) {
            endpointProperties.uris = uris;
            return this;
        }

        public EndpointProperties build() {
            return endpointProperties;
        }
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
