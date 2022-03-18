package org.kin.spring.rsocket.example.requester;

import org.kin.spring.rsocket.support.EnableSpringRSocketServiceReference;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.rsocket.RSocketRequester;

/**
 * @author huangjianqin
 * @date 2021/8/23
 */
@Configuration
public class SpringRSocketRequesterConfiguration {
    /**
     * 使用{@link org.kin.spring.rsocket.support.EnableSpringRSocketServiceReference}需要{@link RSocketRequester} bean, 而
     * 使用{@link org.kin.spring.rsocket.support.EnableLoadBalanceSpringRSocketServiceReference}并不需要
     * @see EnableSpringRSocketServiceReference
     */
//    @Bean
//    public RSocketRequester rsocketRequester(RSocketStrategies strategies) {
//        return RSocketRequester.builder()
//                .dataMimeType(MimeTypeUtils.APPLICATION_JSON)
//                .metadataMimeType(MimeType.valueOf(WellKnownMimeType.MESSAGE_RSOCKET_COMPOSITE_METADATA.getString()))
//                .rsocketStrategies(strategies)
//                .tcp("0.0.0.0", 9000);
//    }
}
