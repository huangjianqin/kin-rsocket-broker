package org.kin.rsocket.conf.h2;

import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.conf.ConfDiamond;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * @author huangjianqin
 * @date 2021/4/3
 */
@ConditionalOnExpression("!'${kin.rsocket.broker.conf.h2}'.isEmpty()")
@Configuration
@EnableConfigurationProperties(H2ConfDiamondProperties.class)
public class RSocketH2ConfDiamondAutoConfiguration {
    @Autowired
    private H2ConfDiamondProperties h2ConfDiamondProperties;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @ConditionalOnProperty("kin.rsocket.broker.conf.h2.dbPath")
    @Bean
    public ConfDiamond h2ConfDiamond() {
        String dbPath = h2ConfDiamondProperties.getDbPath();
        if (StringUtils.isBlank(dbPath)) {
            //如果没有配置, 则使用默认路径
            File rsocketRootDir = new File(System.getProperty("user.home"), ".rsocket");
            if (!rsocketRootDir.exists()) {
                rsocketRootDir.mkdirs();
            }
            dbPath = new File(rsocketRootDir, "appsConfig.db").getAbsolutePath();
        }
        return new H2ConfDiamond(dbPath);
    }
}
