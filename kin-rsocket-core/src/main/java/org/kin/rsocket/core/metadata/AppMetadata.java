package org.kin.rsocket.core.metadata;

import io.netty.buffer.ByteBuf;
import org.kin.framework.utils.CollectionUtils;
import org.kin.rsocket.core.RSocketMimeType;
import org.kin.rsocket.core.domain.AppVO;
import org.kin.rsocket.core.utils.JSON;
import org.kin.rsocket.core.utils.Topologys;

import java.util.*;

/**
 * application metadata, json format
 *
 * @author huangjianqin
 * @date 2021/3/24
 */
public final class AppMetadata implements MetadataAware {
    /** instance hashcode ID, auto generated by credential and UUID */
    private Integer instanceId;
    /** application uuid, almost uuid */
    private String uuid;
    /** 权重, 越大, 该服务被路由的概率越大 */
    private int weight = 1;
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
    /** 需要开启p2p的服务gsv */
    private volatile Set<String> p2pServiceIds = Collections.emptySet();
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

    private AppMetadata() {
    }

    @Override
    public RSocketMimeType mimeType() {
        return RSocketMimeType.APPLICATION;
    }

    @Override
    public ByteBuf getContent() {
        return JSON.writeByteBuf(this);
    }

    @Override
    public void load(ByteBuf byteBuf) {
        JSON.updateFieldValue(byteBuf, this);
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

    /** rsocket service通知broker更新rsocket port */
    public void updateRSocketPorts(Map<Integer, String> rsocketPorts) {
        this.rsocketPorts = rsocketPorts;
    }

    /** rsocket service通知broker更新rsocket web port */
    public void updateWebPort(int webPort) {
        this.webPort = webPort;
    }

    /** rsocket service通知broker更新rsocket management port */
    public void updateManagementPort(int managementPort) {
        this.managementPort = managementPort;
    }

    /** rsocket service注册时, broker给更新instance id */
    public void updateInstanceId(Integer id) {
        this.instanceId = id;
    }

    /** rsocket service注册时, broker给更新注册时间 */
    public void updateConnectedAt(Date connectedAt) {
        this.connectedAt = connectedAt;
    }

    /** 更新rsocket service p2p订阅服务 */
    public void updateP2pServiceIds(Set<String> p2pServiceIds) {
        this.p2pServiceIds = p2pServiceIds;
    }

    /**
     * 转换成{@link AppVO}
     */
    public AppVO toVo() {
        return AppVO.builder()
                .id(instanceId).uuid(uuid).weight(weight)
                .name(name).nameSpace(nameSpace)
                .description(description).device(device)
                .rsocketPorts(rsocketPorts).ip(ip)
                .brokers(brokers).topology(topology)
                .secure(secure).webPort(webPort)
                .managementPort(managementPort)
                .sdk(sdk).developers(developers)
                .metadata(metadata).connectedAt(connectedAt)
                .build();
    }

    //----------------------------------------------------------------builder----------------------------------------------------------------
    public static Builder builder() {
        return new Builder();
    }

    /** builder **/
    public static class Builder {
        private final AppMetadata appMetadata = new AppMetadata();

        public Builder id(Integer id) {
            appMetadata.instanceId = id;
            return this;
        }

        public Builder uuid(String uuid) {
            appMetadata.uuid = uuid;
            return this;
        }

        public Builder weight(int weight) {
            appMetadata.weight = Math.max(1, weight);
            return this;
        }

        public Builder name(String name) {
            appMetadata.name = name;
            return this;
        }

        public Builder nameSpace(String nameSpace) {
            appMetadata.nameSpace = nameSpace;
            return this;
        }

        public Builder description(String description) {
            appMetadata.description = description;
            return this;
        }

        public Builder device(String device) {
            appMetadata.device = device;
            return this;
        }

        public Builder rsocketPorts(Map<Integer, String> rsocketPorts) {
            appMetadata.rsocketPorts = rsocketPorts;
            return this;
        }

        public Builder ip(String ip) {
            appMetadata.ip = ip;
            return this;
        }

        public Builder brokers(List<String> brokers) {
            appMetadata.brokers = brokers;
            return this;
        }

        public Builder p2pServices(Set<String> p2pServices) {
            appMetadata.p2pServiceIds = p2pServices;
            return this;
        }

        public Builder topology(String topology) {
            appMetadata.topology = topology;
            return this;
        }

        public Builder secure(boolean secure) {
            appMetadata.secure = secure;
            return this;
        }

        public Builder webPort(int webPort) {
            appMetadata.webPort = webPort;
            return this;
        }

        public Builder managementPort(int managementPort) {
            appMetadata.managementPort = managementPort;
            return this;
        }

        public Builder sdk(String sdk) {
            appMetadata.sdk = sdk;
            return this;
        }

        public Builder developers(String developers) {
            appMetadata.developers = developers;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            appMetadata.metadata = metadata;
            return this;
        }

        public Builder addMetadata(String name, String value) {
            if (CollectionUtils.isEmpty(appMetadata.metadata)) {
                appMetadata.metadata = new HashMap<>(4);
            }
            appMetadata.metadata.put(name, value);
            return this;
        }

        public Builder humansMd(String humansMd) {
            appMetadata.humansMd = humansMd;
            return this;
        }

        public Builder connectedAt(Date connectedAt) {
            appMetadata.connectedAt = connectedAt;
            return this;
        }

        public AppMetadata build() {
            return appMetadata;
        }
    }

    //getter
    public Integer getInstanceId() {
        return instanceId;
    }

    public String getUuid() {
        return uuid;
    }

    public int getWeight() {
        return weight;
    }

    public String getName() {
        return name;
    }

    public String getNameSpace() {
        return nameSpace;
    }

    public String getDescription() {
        return description;
    }

    public String getDevice() {
        return device;
    }

    public Map<Integer, String> getRsocketPorts() {
        return rsocketPorts;
    }

    public String getIp() {
        return ip;
    }

    public List<String> getBrokers() {
        return brokers;
    }

    public Set<String> getP2pServiceIds() {
        return p2pServiceIds;
    }

    public String getTopology() {
        return topology;
    }

    public boolean isSecure() {
        return secure;
    }

    public int getWebPort() {
        return webPort;
    }

    public int getManagementPort() {
        return managementPort;
    }

    public String getSdk() {
        return sdk;
    }

    public String getDevelopers() {
        return developers;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public String getHumansMd() {
        return humansMd;
    }

    public Date getConnectedAt() {
        return connectedAt;
    }
}