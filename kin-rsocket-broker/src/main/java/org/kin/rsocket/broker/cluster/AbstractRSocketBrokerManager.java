package org.kin.rsocket.broker.cluster;

import org.kin.framework.utils.ExceptionUtils;
import org.kin.rsocket.core.AbstractRSocketFilter;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.application.ConfigChangedEvent;
import org.kin.rsocket.core.event.broker.FilterEnableEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.util.Optional;

/**
 * @author huangjianqin
 * @date 2021/3/29
 */
public abstract class AbstractRSocketBrokerManager implements RSocketBrokerManager {
    @Autowired
    protected ApplicationContext applicationContext;

    /**
     * 处理通过gossip广播的cloud event
     */
    protected void handleCloudEvent(CloudEventData<?> cloudEvent) {
        String type = cloudEvent.getAttributes().getType();
        Optional<?> cloudEventData = cloudEvent.getData();
        cloudEventData.ifPresent(data -> {
            if (FilterEnableEvent.class.getCanonicalName().equals(type)) {
                try {
                    FilterEnableEvent filterEnableEvent = (FilterEnableEvent) data;
                    AbstractRSocketFilter rsocketFilter = (AbstractRSocketFilter) applicationContext.getBean(Class.forName(filterEnableEvent.getFilterClassName()));
                    rsocketFilter.updateEnable(filterEnableEvent.isEnabled());
                } catch (Exception e) {
                    ExceptionUtils.throwExt(e);
                }
            } else if (data instanceof ConfigChangedEvent) {
                ConfigChangedEvent configChangedEvent = (ConfigChangedEvent) data;
                //TODO
//                ConfigurationService configurationService = applicationContext.getBean(ConfigurationService.class);
//                configurationService.put(configChangedEvent.getAppName() + ":" + configChangedEvent.getKey(), configChangedEvent.getVale()).subscribe();
            }
        });
    }
}
