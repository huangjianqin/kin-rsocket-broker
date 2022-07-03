package org.kin.spring.rsocket.example.consumer;

import io.rsocket.metadata.WellKnownMimeType;
import org.kin.framework.utils.NetUtils;
import org.kin.rsocket.core.RSocketAppContext;
import org.kin.rsocket.core.metadata.AppMetadata;
import org.kin.rsocket.core.metadata.BearerTokenMetadata;
import org.kin.rsocket.core.utils.Topologys;
import org.kin.spring.rsocket.example.UserService;
import org.kin.spring.rsocket.support.EnableSpringRSocketServiceReference;
import org.kin.spring.rsocket.support.SpringRSocketServiceReference;
import org.kin.spring.rsocket.support.SpringRSocketServiceReferenceFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.codec.ByteBufferEncoder;
import org.springframework.core.env.Environment;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

/**
 * 与kin-rsocket-broker整合样例
 *
 * @author huangjianqin
 * @date 2022/7/3
 */
//@SpringBootApplication
@EnableSpringRSocketServiceReference
public class SpringRSocketRequesterApplication {
    private static final String JWT_TOKEN = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJNb2NrIiwiYXVkIjoia2luIiwic2FzIjpbImRlZmF1bHQiXSwicm9sZXMiOlsiaW50ZXJuYWwiXSwiaXNzIjoiS2luUlNvY2tldEJyb2tlciIsImlkIjoiNmFkNTNiNDItMjEyNi00ZjE2LWEwNzQtY2I4MjBjZGZlYjFhIiwib3JncyI6WyJkZWZhdWx0Il0sImlhdCI6MTYxODI4MDkxOH0.e8O1ZSpoBKW2UJYXqnLM8d9zmLNDUa-AQsRu-cig0N9R2A-4-9TwN1mz4uuftigU6iX0EjxNCCghd6IldvcjK88af-MeMUkdEx4_83dBm0Ugjp70au0_BacF83MBfYBnDK_hZ3Ftu2_Plp83dLiHbU-h3TK4VT4xfDM5LbYFR_4zvTDK_42lnJqrP1HDFwcZcHLeHhhhZmzVhpLiUnkDRDGW4P7RBASOacI89IMw2zc15aLrRqs3qZRRxFwX0huHVI2fZFF_GC5tYh47RqNcDSWcc_vwo-PuTPTCkGvDM7QvpYzpdM95LsPC6Z95vfv0VRwSCewlCj5IINqnzvY-ZA";

    @Autowired
    private Environment environment;

    public static void main(String[] args) {
        SpringApplication.run(SpringRSocketRequesterApplication.class, args);
    }

    @Bean
    @SpringRSocketServiceReference(interfaceClass = UserService.class, service = "org.kin.rsocket.example.UserService")
    public SpringRSocketServiceReferenceFactoryBean<UserService> userService() {
        return new SpringRSocketServiceReferenceFactoryBean<>();
    }

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

    @Bean
    public RSocketRequester rsocketRequester(RSocketStrategies strategies) {
        //app metadata
        AppMetadata appMetadata = getAppMetadata();
        // authentication
        BearerTokenMetadata bearerTokenMetadata = BearerTokenMetadata.jwt(JWT_TOKEN.toCharArray());

        return RSocketRequester.builder()
                .dataMimeType(MimeTypeUtils.APPLICATION_JSON)
                .metadataMimeType(MimeType.valueOf(WellKnownMimeType.MESSAGE_RSOCKET_COMPOSITE_METADATA.getString()))
                .rsocketStrategies(strategies)
                .setupMetadata(appMetadata.getContent(), MimeType.valueOf("message/x.rsocket.application+json"))
                .setupMetadata(bearerTokenMetadata.getContent(), MimeType.valueOf(WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION.getString()))
                .tcp("0.0.0.0", 9999);
    }

    /** 获取app元数据 */
    private AppMetadata getAppMetadata() {
        //app metadata
        AppMetadata.Builder builder = AppMetadata.builder();
        builder.uuid(RSocketAppContext.ID);
        builder.name(environment.getProperty("spring.application.name"));
        builder.ip(NetUtils.getIp());
        builder.device("SpringBoot");
        builder.topology(Topologys.INTRANET);
        //web port
        builder.webPort(environment.getProperty("server.port", Integer.TYPE, 0));
        //management port
        builder.managementPort(environment.getProperty("management.server.port", Integer.TYPE, 0));
        builder.secure(true);
        return builder.build();
    }
}
