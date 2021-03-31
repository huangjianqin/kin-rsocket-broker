package org.kin.rsocket.broker.cluster;

/**
 * 集群中broker数据
 * todo 数据设置
 *
 * @author huangjianqin
 * @date 2021/3/29
 */
public class Broker {
    /** broker id */
    private String id;
    /** rsocket schema */
    private String schema;
    /** ip */
    private String ip;
    /** externalDomain */
    private String externalDomain;
    /** rsocket port */
    private int port;
    /** broker status */
    private Integer status = 1;
    /** broker start time */
    private long startedAt;

    public static Broker of(String id, String schema,
                            String ip, String externalDomain,
                            int port) {
        Broker inst = new Broker();
        inst.id = id;
        inst.schema = schema;
        inst.ip = ip;
        inst.externalDomain = externalDomain;
        inst.port = port;
        inst.startedAt = System.currentTimeMillis();
        return inst;
    }

    public String getUrl() {
        return schema + "://" + ip + ":" + port;
    }

    public String getAliasUrl() {
        if (externalDomain.contains("://")) {
            return this.externalDomain;
        } else {
            return schema + "://" + externalDomain + ":" + port;
        }
    }

    /**
     * broker是否活跃
     */
    public boolean isActive() {
        return status >= 1;
    }

    //setter && getter
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getExternalDomain() {
        return externalDomain;
    }

    public void setExternalDomain(String externalDomain) {
        this.externalDomain = externalDomain;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(long startedAt) {
        this.startedAt = startedAt;
    }
}
