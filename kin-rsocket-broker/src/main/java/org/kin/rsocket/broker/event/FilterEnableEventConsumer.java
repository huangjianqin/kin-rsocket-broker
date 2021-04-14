package org.kin.rsocket.broker.event;

import org.kin.framework.utils.ExceptionUtils;
import org.kin.rsocket.broker.AbstractRSocketFilter;
import org.kin.rsocket.core.event.CloudEventConsumer;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.CloudEventSupport;
import org.kin.rsocket.core.event.FilterEnableEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import reactor.core.publisher.Mono;

/**
 * @author huangjianqin
 * @date 2021/3/31
 */
public final class FilterEnableEventConsumer implements CloudEventConsumer {
    @Autowired
    protected ApplicationContext applicationContext;

    @Override
    public boolean shouldAccept(CloudEventData<?> cloudEvent) {
        return FilterEnableEvent.class.getCanonicalName().equals(cloudEvent.getAttributes().getType());
    }

    @Override
    public Mono<Void> consume(CloudEventData<?> cloudEvent) {
        try {
            FilterEnableEvent event = CloudEventSupport.unwrapData(cloudEvent, FilterEnableEvent.class);
            AbstractRSocketFilter rsocketFilter = (AbstractRSocketFilter) applicationContext.getBean(Class.forName(event.getFilterClassName()));
            rsocketFilter.updateEnable(event.isEnabled());
        } catch (Exception e) {
            ExceptionUtils.throwExt(e);
        }
        return Mono.empty();
    }
}
