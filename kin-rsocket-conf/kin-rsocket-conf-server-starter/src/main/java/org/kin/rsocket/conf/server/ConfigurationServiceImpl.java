package org.kin.rsocket.conf.server;

import org.kin.rsocket.conf.ConfDiamond;
import org.kin.rsocket.core.RSocketService;
import org.kin.rsocket.core.conf.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * @author huangjianqin
 * @date 2022/4/9
 */
@RSocketService(ConfigurationService.class)
@Service
public class ConfigurationServiceImpl implements ConfigurationService {
    @Autowired
    private ConfDiamond confDiamond;

    @Override
    public Mono<String> get(String appName, String key) {
        return confDiamond.get(appName, key);
    }
}
