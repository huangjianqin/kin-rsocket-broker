package org.kin.rsocket.broker.discovery;

import org.springframework.cloud.client.ServiceInstance;

import java.io.Serializable;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * @author huangjianqin
 * @date 2021/3/25
 */
public class RSocketServiceInstance implements ServiceInstance, Serializable {
    private static final long serialVersionUID = -4533407024936958349L;
    /** 实例id */
    private String instanceId;
    /** 服务id */
    private String serviceId;
    /** host */
    private String host;
    /** port */
    private int port;
    /** schema */
    private String schema = "tcp";
    /** uri */
    private String uri;
    /** 是否ssl */
    private boolean secure = false;
    /** 元数据 */
    private Map<String, String> metadata = new HashMap<>();

    @Override
    public String getInstanceId() {
        return this.instanceId;
    }

    @Override
    public String getServiceId() {
        return this.serviceId;
    }

    @Override
    public String getHost() {
        return this.host;
    }

    @Override
    public int getPort() {
        return this.port;
    }

    @Override
    public boolean isSecure() {
        return this.secure;
    }

    @Override
    public URI getUri() {
        return URI.create(uri);
    }

    @Override
    public Map<String, String> getMetadata() {
        return this.metadata;
    }

    @Override
    public String getScheme() {
        return this.schema;
    }

    //setter && getter
    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
}
