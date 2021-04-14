package org.kin.rsocket.service;

import org.kin.rsocket.core.utils.Topologys;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author huangjianqin
 * @date 2021/3/28
 */
@ConfigurationProperties(prefix = "kin.rsocket")
public class RSocketServiceProperties {
    /** schema, such as tcp, local */
    private String schema = "tcp";
    /** listen port, default is 9998`, 0 means to disable listen */
    private Integer port = 9998;
    /** broker url, such tcp://127.0.0.1:9999 */
    private List<String> brokers;
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
    private Integer timeout = 3000;
    /** endpoints: interface full name to endpoint url */
    private List<EndpointProperties> endpoints;

    //setter && getter
    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
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

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }
}
