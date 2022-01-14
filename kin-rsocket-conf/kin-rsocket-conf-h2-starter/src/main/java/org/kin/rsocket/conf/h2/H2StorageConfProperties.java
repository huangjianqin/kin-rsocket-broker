package org.kin.rsocket.conf.h2;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author huangjianqin
 * @date 2022/1/14
 */
@ConfigurationProperties(prefix = "kin.rsocket.broker.conf.h2")
public class H2StorageConfProperties {
    /** h2 db path */
    private String dbPath;

    //setter && getter
    public String getDbPath() {
        return dbPath;
    }

    public void setDbPath(String dbPath) {
        this.dbPath = dbPath;
    }
}
