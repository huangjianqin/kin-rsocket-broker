package org.kin.rsocket.core.metadata;

import io.netty.buffer.ByteBuf;
import org.kin.rsocket.core.RSocketMimeType;
import org.kin.rsocket.core.utils.JSON;
import org.kin.rsocket.core.utils.Topologys;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * application metadata, json format
 *
 * @author huangjianqin
 * @date 2021/3/24
 */
public class AppMetadata implements MetadataAware {
    /** instance hashcode ID, auto generated by credential and UUID */
    private Integer id;
    /** application uuid, almost uuid */
    private String uuid;
    /** 权重, 越大, 该服务被路由的概率越大 */
    private int powerRating = 1;
    /** app name */
    private String name;
    /** name space */
    private String nameSpace;
    /** description */
    private String description;
    /** device information */
    private String device;
    /** rsocket schema */
    private Map<Integer, String> rsocketPorts;
    /** ip */
    private String ip;
    /** connected brokers */
    private List<String> brokers;
    /** topology, such as intranet or internet */
    private String topology = Topologys.INTRANET;
    /** secure or not */
    private boolean secure = false;
    /** web port */
    private int webPort;
    /** management port for Spring Boot actuator */
    private int managementPort;
    /** sdk and RSocket protocol version */
    private String sdk = "Kin-RSocket-0.1.0.0";
    /** developers, format as email list: xxx <xxx@foobar.com>, yyy <yyy@foobar.com> */
    private String developers;
    /** metadata */
    private Map<String, String> metadata;
    /** humans.md from classpath todo 优化:有没有必要将整个文件内容读进内存,可以改为每次都请求服务, 但会增加网络流量 */
    private String humansMd;
    /** connected timestamp */
    private Date connectedAt;

    public static AppMetadata of(ByteBuf byteBuf) {
        AppMetadata metadata = new AppMetadata();
        metadata.load(byteBuf);
        return metadata;
    }

    /**
     * 设置powerRating
     */
    public void powerRating(int powerRating) {
        this.powerRating = Math.max(1, powerRating);
    }

    /**
     * 添加Metadata
     */
    public void addMetadata(String name, String value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(name, value);
    }

    /**
     * 获取Metadata
     */
    public String getMetadata(String name) {
        if (this.metadata == null) {
            return null;
        }
        return metadata.get(name);
    }

    @Override
    public RSocketMimeType mimeType() {
        return RSocketMimeType.Application;
    }

    @Override
    public ByteBuf getContent() {
        return JSON.writeByteBuf(this);
    }

    @Override
    public void load(ByteBuf byteBuf) {
        JSON.updateFieldValue(byteBuf, this);
    }

    //setter && getter
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public int getPowerRating() {
        return powerRating;
    }

    public void setPowerRating(int powerRating) {
        this.powerRating = powerRating;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNameSpace() {
        return nameSpace;
    }

    public void setNameSpace(String nameSpace) {
        this.nameSpace = nameSpace;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getHumansMd() {
        return humansMd;
    }

    public void setHumansMd(String humansMd) {
        this.humansMd = humansMd;
    }

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public Map<Integer, String> getRsocketPorts() {
        return rsocketPorts;
    }

    public void setRsocketPorts(Map<Integer, String> rsocketPorts) {
        this.rsocketPorts = rsocketPorts;
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

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public int getWebPort() {
        return webPort;
    }

    public void setWebPort(int webPort) {
        this.webPort = webPort;
    }

    public int getManagementPort() {
        return managementPort;
    }

    public void setManagementPort(int managementPort) {
        this.managementPort = managementPort;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public String getDevelopers() {
        return developers;
    }

    public void setDevelopers(String developers) {
        this.developers = developers;
    }

    public String getSdk() {
        return sdk;
    }

    public void setSdk(String sdk) {
        this.sdk = sdk;
    }

    public Date getConnectedAt() {
        return connectedAt;
    }

    public void setConnectedAt(Date connectedAt) {
        this.connectedAt = connectedAt;
    }
}