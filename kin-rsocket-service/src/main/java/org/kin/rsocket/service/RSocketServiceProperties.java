package org.kin.rsocket.service;

import org.kin.rsocket.core.utils.Topologys;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author huangjianqin
 * @date 2021/3/28
 */
public class RSocketServiceProperties {
    /** schema, such as tcp, local */
    private String schema = "tcp";
    /** listen port, 0 means to disable listen */
    private int port;
    /** broker url, such tcp://127.0.0.1:9999 */
    private List<String> brokers = Collections.singletonList("tcp://0.0.0.0:9999");
    /** topology, intranet or internet */
    private String topology = Topologys.INTRANET;
    /** metadata */
    private Map<String, String> metadata = Collections.emptyMap();
    /** group for exposed service */
    private String group = "";
    /** version for exposed services */
    public String version = "";
    /** JWT token */
    private String jwtToken;
    /** request/response timeout, and default value is 3000 and unit is millisecond */
    private int timeout = 3000;
    /** endpoints: interface full name to endpoint url */
    private List<EndpointProperties> endpoints;

    //setter && getter
    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
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

    public String getJwtToken() {
        return jwtToken;
    }

    public void setJwtToken(String jwtToken) {
        this.jwtToken = jwtToken;
    }

    public List<String> getBrokers() {
        return brokers;
    }

    public void setBrokers(List<String> brokers) {
        this.brokers = brokers;
    }

    public String getTopology() {
        return topology;
    }

    public void setTopology(String topology) {
        this.topology = topology;
    }

    public List<EndpointProperties> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(List<EndpointProperties> endpoints) {
        this.endpoints = endpoints;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    //----------------------------------------------------------------------------------------------------------------------------------------
    public static Builder builder() {
        return new Builder();
    }

    /** builder **/
    public static class Builder {
        private RSocketServiceProperties rsocketServiceProperties = new RSocketServiceProperties();

        public Builder schema(String schema) {
            rsocketServiceProperties.schema = schema;
            return this;
        }

        public Builder port(Integer port) {
            rsocketServiceProperties.port = port;
            return this;
        }

        public Builder brokers(List<String> brokers) {
            rsocketServiceProperties.brokers = brokers;
            return this;
        }

        public Builder brokers(String... brokers) {
            return brokers(Arrays.asList(brokers));
        }

        public Builder topology(String topology) {
            rsocketServiceProperties.topology = topology;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            rsocketServiceProperties.metadata = metadata;
            return this;
        }

        public Builder group(String group) {
            rsocketServiceProperties.group = group;
            return this;
        }

        public Builder version(String version) {
            rsocketServiceProperties.version = version;
            return this;
        }

        public Builder jwtToken(String jwtToken) {
            rsocketServiceProperties.jwtToken = jwtToken;
            return this;
        }

        public Builder timeout(Integer timeout) {
            rsocketServiceProperties.timeout = timeout;
            return this;
        }

        public Builder endpoints(List<EndpointProperties> endpoints) {
            rsocketServiceProperties.endpoints = endpoints;
            return this;
        }

        public Builder endpoints(EndpointProperties... endpoints) {
            return endpoints(Arrays.asList(endpoints));
        }

        public RSocketServiceProperties build() {
            return rsocketServiceProperties;
        }
    }
}
