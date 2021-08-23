package org.kin.spring.rsocket.example.requester;

import io.rsocket.metadata.WellKnownMimeType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

/**
 * @author huangjianqin
 * @date 2021/8/23
 */
@Configuration
public class SpringRSocketRequesterConfiguration {
    @Bean
    public RSocketRequester rsocketRequester(RSocketStrategies strategies) {
        return RSocketRequester.builder()
                .dataMimeType(MimeTypeUtils.APPLICATION_JSON)
                .metadataMimeType(MimeType.valueOf(WellKnownMimeType.MESSAGE_RSOCKET_COMPOSITE_METADATA.getString()))
                .rsocketStrategies(strategies)
                .tcp("0.0.0.0", 9000);
    }
}
