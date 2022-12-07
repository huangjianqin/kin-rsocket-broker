package org.kin.rsocket.core.event;

import io.cloudevents.CloudEvent;
import org.kin.framework.utils.ClassUtils;
import reactor.core.publisher.Mono;

import java.lang.reflect.Type;
import java.util.List;

/**
 * 自动检测, 并判断cloud event包装的type是否与实际event type一致
 *
 * @param <T> event class
 * @author huangjianqin
 * @date 2021/4/20
 */
public abstract class AbstractCloudEventConsumer<T extends CloudEventSupport> implements CloudEventConsumer {
    /** 目标cloud event class */
    private final Class<? extends CloudEventSupport> eventClass;

    @SuppressWarnings("unchecked")
    public AbstractCloudEventConsumer() {
        List<Type> actualTypes = ClassUtils.getSuperClassGenericActualTypes(getClass());
        eventClass = (Class<? extends CloudEventSupport>) actualTypes.get(0);
    }

    @Override
    public final boolean shouldAccept(CloudEvent cloudEvent) {
        return eventClass.getName().equalsIgnoreCase(cloudEvent.getType());
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<Void> consume(CloudEvent cloudEvent) {
        return Mono.fromRunnable(() -> consume(cloudEvent, (T) CloudEventSupport.unwrapData(cloudEvent)));
    }

    /**
     * consume cloud event具体逻辑
     *
     * @param cloudEvent cloud event
     * @param event      实际event
     * @return consume complete signal
     */
    protected abstract void consume(CloudEvent cloudEvent, T event);
}
