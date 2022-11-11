package org.kin.rsocket.service.boot;

import org.apache.commons.io.FileUtils;
import org.kin.framework.utils.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author huangjianqin
 * @date 2021/4/17
 */
@ConfigurationProperties(prefix = "kin.rsocket")
public class RSocketServiceProperties extends org.kin.rsocket.service.RSocketServiceProperties {
    @PostConstruct
    public void loadJwtToken() throws IOException {
        String jwtToken = getJwtToken();
        if (StringUtils.isBlank(jwtToken)) {
            return;
        }

        File jwtTokenFile = new File(jwtToken);
        if (!jwtTokenFile.exists() || jwtTokenFile.isDirectory()) {
            return;
        }

        //如果配置的jwt token文件路径, 则加载进来并覆盖
        setJwtToken(FileUtils.readFileToString(jwtTokenFile, StandardCharsets.UTF_8.toString()));
    }
}
