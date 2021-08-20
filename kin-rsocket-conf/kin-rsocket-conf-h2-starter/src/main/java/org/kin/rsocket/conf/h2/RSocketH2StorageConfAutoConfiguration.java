package org.kin.rsocket.conf.h2;

import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.conf.ConfDiamond;
import org.kin.rsocket.conf.RSocketConfDiamondConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.io.File;

/**
 * @author huangjianqin
 * @date 2021/4/3
 */
@RSocketConfDiamondConfiguration
public class RSocketH2StorageConfAutoConfiguration {
    @Bean
    @ConditionalOnProperty("kin.rsocket.broker.conf.h2.dbPath")
    public ConfDiamond configurationService(@Value("${kin.rsocket.broker.conf.h2.dbPath}") String dbPath) {
        if (StringUtils.isBlank(dbPath)) {
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
