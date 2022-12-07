package org.kin.rsocket.broker.event;

import io.cloudevents.CloudEvent;
import org.kin.framework.utils.ExceptionUtils;
import org.kin.rsocket.broker.AbstractRSocketFilter;
import org.kin.rsocket.core.event.AbstractCloudEventConsumer;
import org.kin.rsocket.core.event.FilterEnableEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * @author huangjianqin
 * @date 2021/3/31
 */
public final class FilterEnableEventConsumer extends AbstractCloudEventConsumer<FilterEnableEvent> {
    @Autowired
    private ApplicationContext applicationContext;

    @Override
    public void consume(CloudEvent cloudEvent, FilterEnableEvent event) {
        try {
            AbstractRSocketFilter rsocketFilter = (AbstractRSocketFilter) applicationContext.getBean(Class.forName(event.getFilterClassName()));
            rsocketFilter.updateEnable(event.isEnabled());
        } catch (Exception e) {
            ExceptionUtils.throwExt(e);
        }
    }
}
