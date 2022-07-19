package org.kin.rsocket.cloud.function;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author huangjianqin
 * @date 2022/7/19
 */
@Configuration
public class RSocketFunctionAutoConfiguration {
    @Bean
    public RSocketFunctionRegistrar rsocketFunctionRegistrar() {
        return new RSocketFunctionRegistrar();
    }
}
