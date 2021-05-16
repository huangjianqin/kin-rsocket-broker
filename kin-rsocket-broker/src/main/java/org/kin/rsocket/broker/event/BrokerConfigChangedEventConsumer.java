package org.kin.rsocket.broker.event;

import org.kin.framework.utils.PropertiesUtils;
import org.kin.rsocket.conf.ConfDiamond;
import org.kin.rsocket.core.event.AbstractCloudEventConsumer;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.ConfigChangedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;

import java.util.Properties;

/**
 * broker端{@link org.kin.rsocket.core.event.ConfigChangedEvent}处理
 * 目前仅仅只有gossip支持在broker cluster广播{@link ConfigChangedEvent}
 *
 * @author huangjianqin
 * @date 2021/3/31
 */
public final class BrokerConfigChangedEventConsumer extends AbstractCloudEventConsumer<ConfigChangedEvent> {
    @Autowired
    private ConfDiamond confDiamond;

    @Override
    public Mono<Void> consume(CloudEventData<?> cloudEventData, ConfigChangedEvent event) {
        Properties confs = PropertiesUtils.loadPropertiesContent(event.getContent());

        for (String key : confs.stringPropertyNames()) {
            confDiamond.put(event.getAppName(), key, confs.getProperty(key)).subscribe();
        }

        return Mono.empty();
    }
}
