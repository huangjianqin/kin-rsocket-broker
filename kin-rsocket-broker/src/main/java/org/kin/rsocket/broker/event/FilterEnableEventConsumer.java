package org.kin.rsocket.broker.event;

import org.kin.framework.utils.ExceptionUtils;
import org.kin.rsocket.broker.AbstractRSocketFilter;
import org.kin.rsocket.core.event.AbstractCloudEventConsumer;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.FilterEnableEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * @author huangjianqin
 * @date 2021/3/31
 */
public final class FilterEnableEventConsumer extends AbstractCloudEventConsumer<FilterEnableEvent> {
    @Autowired
    protected ApplicationContext applicationContext;

    @Override
    public Mono<Void> consume(CloudEventData<?> cloudEventData, FilterEnableEvent event) {
        if (Objects.nonNull(event)) {
            try {
                AbstractRSocketFilter rsocketFilter = (AbstractRSocketFilter) applicationContext.getBean(Class.forName(event.getFilterClassName()));
                rsocketFilter.updateEnable(event.isEnabled());
            } catch (Exception e) {
                ExceptionUtils.throwExt(e);
            }
        }

        return Mono.empty();
    }
}
