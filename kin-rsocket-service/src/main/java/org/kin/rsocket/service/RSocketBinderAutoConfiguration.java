package org.kin.rsocket.service;

import io.rsocket.SocketAcceptor;
import org.kin.rsocket.core.RSocketBinder;
import org.kin.rsocket.core.RSocketBinderBuilderCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author huangjianqin
 * @date 2021/3/28
 */
@Configuration
@ConditionalOnExpression("${kin.rsocket.port:0}!=0")
public class RSocketBinderAutoConfiguration {
    @Autowired
    private RSocketServiceProperties config;

    @Bean(initMethod = "start", destroyMethod = "close")
    public RSocketBinder rsocketListener(ObjectProvider<RSocketBinderBuilderCustomizer> customizers) {
        RSocketBinder.Builder builder = RSocketBinder.builder();
        customizers.orderedStream().forEach((customizer) -> customizer.customize(builder));
        return builder.build();
    }

    @Bean
    public RSocketBinderBuilderCustomizer defaultRSocketBinderBuilderCustomizer(@Autowired SocketAcceptor socketAcceptor) {
        return builder -> {
            builder.acceptor(socketAcceptor);
            builder.listen(config.getSchema(), config.getPort());
        };
    }
}
