package org.kin.rsocket.core.event;

import org.kin.framework.utils.ClassUtils;
import reactor.core.publisher.Mono;

import java.lang.reflect.Type;
import java.util.List;

/**
 * 泛型类型即cloud event class
 * 自动检测, 并判断CloudEventData包装的cloud event class是否一致
 *
 * @author huangjianqin
 * @date 2021/4/20
 */
public abstract class AbstractCloudEventConsumer<T extends CloudEventSupport> implements CloudEventConsumer {
    /** 目标cloud event class */
    private final Class<? extends CloudEventSupport> cloudEventClass;

    @SuppressWarnings("unchecked")
    public AbstractCloudEventConsumer() {
        List<Type> actualTypes = ClassUtils.getSuperClassGenericActualTypes(getClass());
        cloudEventClass = (Class<? extends CloudEventSupport>) actualTypes.get(0);
    }

    @Override
    public final boolean shouldAccept(CloudEventData<?> cloudEventData) {
        return cloudEventClass.getCanonicalName().equalsIgnoreCase(cloudEventData.getAttributes().getType());
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<Void> consume(CloudEventData<?> cloudEventData) {
        return consume(cloudEventData, (T) CloudEventSupport.unwrapData(cloudEventData, cloudEventClass));
    }

    protected abstract Mono<Void> consume(CloudEventData<?> cloudEventData, T cloudEvent);
}
