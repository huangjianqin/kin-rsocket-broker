package org.kin.rsocket.broker.cluster;

/**
 * 集群中broker信息
 *
 * @author huangjianqin
 * @date 2021/3/29
 */
public final class BrokerInfo {
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
    private long startTime;
    /** broker暴露的web port */
    private int webPort;

    public static BrokerInfo of(String id, String schema,
                                String ip, String externalDomain,
                                int port, int webPort) {
        BrokerInfo info = new BrokerInfo();
        info.id = id;
        info.schema = schema;
        info.ip = ip;
        info.externalDomain = externalDomain;
        info.port = port;
        info.startTime = System.currentTimeMillis();
        info.webPort = webPort;
        return info;
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

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public int getWebPort() {
        return webPort;
    }

    public void setWebPort(int webPort) {
        this.webPort = webPort;
    }
}
