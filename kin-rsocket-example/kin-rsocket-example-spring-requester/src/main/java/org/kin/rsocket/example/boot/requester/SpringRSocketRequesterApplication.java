package org.kin.rsocket.example.boot.requester;

import io.rsocket.metadata.WellKnownMimeType;
import org.kin.framework.utils.NetUtils;
import org.kin.rsocket.core.RSocketAppContext;
import org.kin.rsocket.core.metadata.AppMetadata;
import org.kin.rsocket.core.metadata.BearerTokenMetadata;
import org.kin.rsocket.core.utils.Topologys;
import org.kin.rsocket.example.boot.UserService;
import org.kin.rsocket.service.boot.support.EnableRSocketServiceReference;
import org.kin.rsocket.service.boot.support.RSocketServiceReference;
import org.kin.rsocket.service.boot.support.RSocketServiceReferenceFactoryBean;
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
@EnableRSocketServiceReference
public class SpringRSocketRequesterApplication {
    private static final String JWT_TOKEN = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJNb2NrIiwiYXVkIjoia2luIiwic2FzIjpbImRlZmF1bHQiXSwicm9sZXMiOlsiaW50ZXJuYWwiXSwiaXNzIjoiS2luUlNvY2tldEJyb2tlciIsImlkIjoiZDczZTE4ZWYtOTgwNS00ZmI3LWIyMTItOGZkNzhhOTU1YzE3Iiwib3JncyI6WyJkZWZhdWx0Il0sImlhdCI6MTY2MTgyNTQyNH0.Iv9WyGszd-QBWvGVtixUzG4mjF7i81tJr65CgGNDSxZrse5I174VnXlcpawKmcUSAlFAT4313MrjGecAxB0bucDXysP6DdLL6tq93kvBu1tCGvIKqz8FfaNBpPWtwljT5_KzwuBMjwxpOOkQFHrfITwNsrWCMrHtC-3wGZQsMXlu9Ysz8SBqwcPWH3GTox3jm8HoccGfLuIoWg6JQPAMZAi3eLC5kQtELmMqTfIqhd38Lj_yJmG25oAxtWLmZEzPT_6aCs6Md1bqz_7ZGALlJxn6z_dcLhx9XrOge7T57w223X5G0JtYhTReOvtzF1dBdeyvkkF9GLj5cKlhImk5DA";

    @Autowired
    private Environment environment;

    public static void main(String[] args) {
        SpringApplication.run(SpringRSocketRequesterApplication.class, args);
    }

    @Bean
    @RSocketServiceReference(interfaceClass = UserService.class, service = "org.kin.rsocket.example.UserService")
    public RSocketServiceReferenceFactoryBean<UserService> userService() {
        return new RSocketServiceReferenceFactoryBean<>();
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
                .tcp("0.0.0.0", 10000);
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
