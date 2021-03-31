package org.kin.rsocket.core.event;

import java.util.UUID;

/**
 * 配置中心配置变化事件
 * 由broker广播给所有application处理
 *
 * @author huangjianqin
 * @date 2021/3/24
 */
public class ConfigChangedEvent implements CloudEventSupport<ConfigChangedEvent> {
    private static final long serialVersionUID = -8370450054209813536L;
    /** config event logic id */
    private String id;
    /** app name */
    private String appName;
    /** config content, properties格式, todo 后续考虑扩展 */
    private String content;
    /** event time(millis) */
    private long time;

    public ConfigChangedEvent() {
        this.id = UUID.randomUUID().toString();
        this.time = System.currentTimeMillis();
    }

    public static ConfigChangedEvent of(String appName, String content) {
        ConfigChangedEvent inst = new ConfigChangedEvent();
        inst.appName = appName;
        inst.content = content;
        return inst;
    }

    //setter && getter
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }
}