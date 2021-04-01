package org.kin.rsocket.spingcloud.conf.client;

import org.kin.rsocket.core.event.CloudEventConsumer;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.CloudEventSupport;
import org.kin.rsocket.core.event.ConfigChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.refresh.ContextRefresher;
import reactor.core.publisher.Mono;

import java.io.StringReader;
import java.util.Properties;

/**
 * service端{@link org.kin.rsocket.core.event.ConfigChangedEvent}处理
 *
 * @author leijuan
 */
public class ConfigChangedEventConsumer implements CloudEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(ConfigChangedEventConsumer.class);

    @Value("${spring.application.name}")
    private String applicationName;
    @Autowired
    private ContextRefresher contextRefresher;
    @Autowired
    private RSocketConfigPropertySourceLocator locator;

    @Override
    public boolean shouldAccept(CloudEventData<?> cloudEvent) {
        return ConfigChangedEvent.class.getCanonicalName().equals(cloudEvent.getAttributes().getType());
    }

    @Override
    public Mono<Void> consume(CloudEventData<?> cloudEvent) {
        //todo
        // replyto support
        // cloudEvent.getExtensions().get("replyto"); rsocket:///REQUEST_FNF/com.xxxx.XxxService#method
        ConfigChangedEvent event = CloudEventSupport.unwrapData(cloudEvent, ConfigChangedEvent.class);
        // validate app name
        if (event != null && applicationName.equalsIgnoreCase(event.getAppName())
                && !locator.getLastContent().equals(event.getContent())) {
            Properties confs = locator.getConfs();
            if (confs != null) {
                try {
                    confs.load(new StringReader(event.getContent()));
                    log.info("Succeed to receive config: ".concat(confs.toString()));
                    contextRefresher.refresh();
                    log.info("Succeed to refresh Application");
                } catch (Exception e) {
                    log.info("Failed to parse the config properties", e);
                }
            }
        }
        return Mono.empty();
    }
}
