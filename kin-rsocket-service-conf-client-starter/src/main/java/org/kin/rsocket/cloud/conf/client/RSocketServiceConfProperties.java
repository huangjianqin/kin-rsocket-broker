package org.kin.rsocket.cloud.conf.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author huangjianqin
 * @date 2021/5/2
 */
@ConfigurationProperties(prefix = "kin.rsocket.conf")
public class RSocketServiceConfProperties {
    /** 是否自动刷新配置 */
    private boolean autoRefresh;

    //setter && getter
    public boolean isAutoRefresh() {
        return autoRefresh;
    }

    public void setAutoRefresh(boolean autoRefresh) {
        this.autoRefresh = autoRefresh;
    }
}
