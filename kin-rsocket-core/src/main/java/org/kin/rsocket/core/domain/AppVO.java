package org.kin.rsocket.core.domain;

import org.kin.rsocket.core.utils.Topologys;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 后台查询app信息
 * <p>
 * 字段描述参考{@link org.kin.rsocket.core.metadata.AppMetadata}
 *
 * @author huangjianqin
 * @date 2021/7/31
 */
public class AppVO implements Serializable {
    private static final long serialVersionUID = -6182384505855098333L;

    private Integer id;
    private String uuid;
    private int weight = 1;
    private String name;
    private String nameSpace;
    private String description;
    private String device;
    private Map<Integer, String> rsocketPorts;
    private String ip;
    private List<String> brokers;
    private String topology = Topologys.INTRANET;
    private boolean secure = false;
    private int webPort;
    private int managementPort;
    private String sdk = "Kin-RSocket-0.1.0.0";
    private String developers;
    private Map<String, String> metadata;
    private Date connectedAt;

    public static Builder builder() {
        return new Builder();
    }

    /** builder **/
    public static class Builder {
        private final AppVO appVO = new AppVO();

        public Builder id(Integer id) {
            appVO.id = id;
            return this;
        }

        public Builder uuid(String uuid) {
            appVO.uuid = uuid;
            return this;
        }

        public Builder weight(int weight) {
            appVO.weight = weight;
            return this;
        }

        public Builder name(String name) {
            appVO.name = name;
            return this;
        }

        public Builder nameSpace(String nameSpace) {
            appVO.nameSpace = nameSpace;
            return this;
        }

        public Builder description(String description) {
            appVO.description = description;
            return this;
        }

        public Builder device(String device) {
            appVO.device = device;
            return this;
        }

        public Builder rsocketPorts(Map<Integer, String> rsocketPorts) {
            appVO.rsocketPorts = rsocketPorts;
            return this;
        }

        public Builder ip(String ip) {
            appVO.ip = ip;
            return this;
        }

        public Builder brokers(List<String> brokers) {
            appVO.brokers = brokers;
            return this;
        }

        public Builder topology(String topology) {
            appVO.topology = topology;
            return this;
        }

        public Builder secure(boolean secure) {
            appVO.secure = secure;
            return this;
        }

        public Builder webPort(int webPort) {
            appVO.webPort = webPort;
            return this;
        }

        public Builder managementPort(int managementPort) {
            appVO.managementPort = managementPort;
            return this;
        }

        public Builder sdk(String sdk) {
            appVO.sdk = sdk;
            return this;
        }

        public Builder developers(String developers) {
            appVO.developers = developers;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            appVO.metadata = metadata;
            return this;
        }

        public Builder connectedAt(Date connectedAt) {
            appVO.connectedAt = connectedAt;
            return this;
        }

        public AppVO build() {
            return appVO;
        }
    }

    //setter && getter
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
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

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public Map<Integer, String> getRsocketPorts() {
        return rsocketPorts;
    }

    public void setRsocketPorts(Map<Integer, String> rsocketPorts) {
        this.rsocketPorts = rsocketPorts;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
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

    public String getSdk() {
        return sdk;
    }

    public void setSdk(String sdk) {
        this.sdk = sdk;
    }

    public String getDevelopers() {
        return developers;
    }

    public void setDevelopers(String developers) {
        this.developers = developers;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public Date getConnectedAt() {
        return connectedAt;
    }

    public void setConnectedAt(Date connectedAt) {
        this.connectedAt = connectedAt;
    }
}
