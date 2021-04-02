package org.kin.rsocket.broker.event;

import org.kin.framework.utils.ExceptionUtils;
import org.kin.rsocket.conf.ConfDiamond;
import org.kin.rsocket.core.event.CloudEventConsumer;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.CloudEventSupport;
import org.kin.rsocket.core.event.ConfigChangedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;

/**
 * broker端{@link org.kin.rsocket.core.event.ConfigChangedEvent}处理
 * 目前仅仅只有gossip支持在broker cluster广播{@link ConfigChangedEvent}
 *
 * @author huangjianqin
 * @date 2021/3/31
 */
public class ConfigChangedEventConsumer implements CloudEventConsumer {
    @Autowired
    private ConfDiamond confDiamond;

    @Override
    public boolean shouldAccept(CloudEventData<?> cloudEvent) {
        return ConfigChangedEvent.class.getCanonicalName().equals(cloudEvent.getAttributes().getType());
    }

    @Override
    public Mono<Void> consume(CloudEventData<?> cloudEvent) {
        ConfigChangedEvent event = CloudEventSupport.unwrapData(cloudEvent, ConfigChangedEvent.class);

        //todo 是否需要校验app name

        Properties confs = new Properties();
        try {
            confs.load(new StringReader(event.getContent()));
        } catch (IOException e) {
            ExceptionUtils.throwExt(e);
        }

        for (String key : confs.stringPropertyNames()) {
            confDiamond.put(event.getAppName() + ConfDiamond.GROUP_KEY_SEPARATOR + key, confs.getProperty(key)).subscribe();
        }

        return Mono.empty();
    }
}
