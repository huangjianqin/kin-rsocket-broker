package org.kin.rsocket.core.event;

import org.kin.framework.Closeable;
import org.springframework.beans.factory.DisposableBean;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.extra.processor.TopicProcessor;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author huangjianqin
 * @date 2021/3/24
 */
public class CloudEventConsumers implements Closeable, DisposableBean {
    /** cloud event consumers */
    private final List<CloudEventConsumer> consumers = new CopyOnWriteArrayList<>();
    /** event topic processor subscribe disposable */
    private final Disposable disposable;

    public CloudEventConsumers(TopicProcessor<CloudEventData<?>> eventProcessor) {
        disposable = eventProcessor.subscribe(cloudEvent -> {
            Flux.fromIterable(consumers)
                    .filter(consumer -> consumer.shouldAccept(cloudEvent))
                    .flatMap(consumer -> consumer.consume(cloudEvent))
                    .subscribe();
        });
    }

    /**
     * 添加一个{@link CloudEventConsumer}
     */
    public void addConsumer(CloudEventConsumer consumer) {
        addConsumers(consumer);
    }

    /**
     * 批量添加{@link CloudEventConsumer}
     */
    public void addConsumers(CloudEventConsumer... consumers) {
        addConsumers(Arrays.asList(consumers));
    }

    /**
     * 批量添加{@link CloudEventConsumer}
     */
    public void addConsumers(Collection<CloudEventConsumer> consumers) {
        this.consumers.addAll(consumers);
    }

    @Override
    public void close() {
        disposable.dispose();
    }

    @Override
    public void destroy() {
        close();
    }
}
