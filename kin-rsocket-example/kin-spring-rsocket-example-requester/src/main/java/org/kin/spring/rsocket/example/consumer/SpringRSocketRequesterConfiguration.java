package org.kin.spring.rsocket.example.consumer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.codec.ByteBufferEncoder;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.messaging.rsocket.RSocketStrategies;

/**
 * @author huangjianqin
 * @date 2021/8/23
 */
@Configuration
public class SpringRSocketRequesterConfiguration {
//    @Autowired
//    private Environment environment;

    /*
     * 使用{@link org.kin.spring.rsocket.support.EnableSpringRSocketServiceReference}需要{@link RSocketRequester} bean, 而
     * 使用{@link org.kin.spring.rsocket.support.EnableLoadBalanceSpringRSocketServiceReference}并不需要
     *
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

    /*
        与kin-rsocket-broker整合
     */
//    private static final String jwtToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJNb2NrIiwiYXVkIjoia2luIiwic2FzIjpbImRlZmF1bHQiXSwicm9sZXMiOlsiaW50ZXJuYWwiXSwiaXNzIjoiS2luUlNvY2tldEJyb2tlciIsImlkIjoiNmFkNTNiNDItMjEyNi00ZjE2LWEwNzQtY2I4MjBjZGZlYjFhIiwib3JncyI6WyJkZWZhdWx0Il0sImlhdCI6MTYxODI4MDkxOH0.e8O1ZSpoBKW2UJYXqnLM8d9zmLNDUa-AQsRu-cig0N9R2A-4-9TwN1mz4uuftigU6iX0EjxNCCghd6IldvcjK88af-MeMUkdEx4_83dBm0Ugjp70au0_BacF83MBfYBnDK_hZ3Ftu2_Plp83dLiHbU-h3TK4VT4xfDM5LbYFR_4zvTDK_42lnJqrP1HDFwcZcHLeHhhhZmzVhpLiUnkDRDGW4P7RBASOacI89IMw2zc15aLrRqs3qZRRxFwX0huHVI2fZFF_GC5tYh47RqNcDSWcc_vwo-PuTPTCkGvDM7QvpYzpdM95LsPC6Z95vfv0VRwSCewlCj5IINqnzvY-ZA";
//
//    @Bean
//    public RSocketRequesterBuilderCustomizer rsocketRequesterBuilderCustomizer() {
//        return (builder, appName, serviceName) -> {
//            //app metadata
//            AppMetadata appMetadata = getAppMetadata();
//            // authentication
//            BearerTokenMetadata bearerTokenMetadata = BearerTokenMetadata.jwt(jwtToken.toCharArray());
//
//            builder.setupMetadata(appMetadata.getContent(), MimeType.valueOf("message/x.rsocket.application+json"));
//            builder.setupMetadata(bearerTokenMetadata.getContent(), MimeType.valueOf(WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION.getString()));
//        };
//    }
//
    @Bean
    public RSocketStrategies rsocketStrategies() {
        return RSocketStrategies.builder()
                .encoders(encoders -> {
                    encoders.add(new Jackson2JsonEncoder());
                    encoders.add(new ByteBufferEncoder());
                })
                .decoders(decoders -> decoders.add(new Jackson2JsonDecoder()))
                .build();
    }
//
//    /** 获取app元数据 */
//    @SuppressWarnings("ResultOfMethodCallIgnored")
//    private AppMetadata getAppMetadata() {
//        //app metadata
//        AppMetadata.Builder builder = AppMetadata.builder();
//        builder.uuid(RSocketAppContext.ID);
//        builder.name(environment.getProperty("spring.application.name"));
//        builder.ip(NetUtils.getIp());
//        builder.device("SpringBoot");
//        builder.topology(Topologys.INTRANET);
//        //web port
//        builder.webPort(environment.getProperty("server.port", Integer.TYPE, 0));
//        //management port
//        builder.managementPort(environment.getProperty("management.server.port", Integer.TYPE, 0));
//        builder.secure(true);
//        return builder.build();
//    }
}
