package org.kin.rsocket.spingcloud.broker.conf.client;

import org.kin.rsocket.core.event.AbstractCloudEventConsumer;
import org.kin.rsocket.core.event.CloudEventData;
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
public class ConfigChangedEventConsumer extends AbstractCloudEventConsumer<ConfigChangedEvent> {
    private static final Logger log = LoggerFactory.getLogger(ConfigChangedEventConsumer.class);

    @Value("${spring.application.name}")
    private String applicationName;
    @Autowired
    private ContextRefresher contextRefresher;
    @Autowired
    private RSocketConfigPropertySourceLocator locator;

    @Override
    public Mono<Void> consume(CloudEventData<?> cloudEventData, ConfigChangedEvent event) {
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
