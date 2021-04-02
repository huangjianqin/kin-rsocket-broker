package org.kin.rsocket.conf.h2;

import org.kin.rsocket.conf.ConfDiamond;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.File;

/**
 * @author huangjianqin
 * @date 2021/4/3
 */
@Configuration
public class RsocketH2StorageConfAutoConfiguration {
    @Bean
    public ConfDiamond configurationService(@Value("${kin.rsocket.broker.conf.h2.dbPath}") String dbPath) {
        if (StringUtils.isEmpty(dbPath)) {
            //如果没有配置, 则使用默认路径
            File rsocketRootDir = new File(System.getProperty("user.home"), ".rsocket");
            if (!rsocketRootDir.exists()) {
                rsocketRootDir.mkdirs();
            }
            dbPath = new File(rsocketRootDir, "appsConfig.db").getAbsolutePath();
        }
        return new H2StorageConfDiamond(dbPath);
    }
}
