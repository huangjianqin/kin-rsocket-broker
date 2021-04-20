package org.kin.rsocket.core.event;

import java.util.Map;

/**
 * @author huangjianqin
 * @date 2021/3/24
 */
public class PortsUpdateEvent implements CloudEventSupport {
    private static final long serialVersionUID = -285999157134947510L;
    /** application id */
    private String appId;
    /** rsocket端口 */
    private Map<Integer, String> rsocketPorts;
    /** application web 端口 */
    private int webPort;
    /** application actuator 端口 */
    private int managementPort;

    //setter && getter
    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public Map<Integer, String> getRsocketPorts() {
        return rsocketPorts;
    }

    public void setRsocketPorts(Map<Integer, String> rsocketPorts) {
        this.rsocketPorts = rsocketPorts;
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
}
