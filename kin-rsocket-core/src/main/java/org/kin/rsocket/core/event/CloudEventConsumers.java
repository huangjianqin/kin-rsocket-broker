package org.kin.rsocket.core.event;

import org.kin.framework.Closeable;
import org.kin.rsocket.core.RSocketAppContext;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author huangjianqin
 * @date 2021/3/24
 */
public final class CloudEventConsumers implements Closeable {
    public static final CloudEventConsumers INSTANCE = new CloudEventConsumers();

    /** cloud event consumers */
    private final List<CloudEventConsumer> consumers = new CopyOnWriteArrayList<>();
    /** event topic processor subscribe disposable */
    private final Disposable disposable;
    /** cloud event处理, 有界无限队列线程池执行, 主要是因为app刷新配置时需要请求broker配置信息并且spring的加载配置也不支持异步, 需要block */
    private static final Scheduler CLOUD_EVENT_CONSUMER_SCHEDULER = Schedulers.newBoundedElastic(3, Integer.MAX_VALUE, "cloudEventConsumer");

    public CloudEventConsumers() {
        disposable = RSocketAppContext.CLOUD_EVENT_SINK.asFlux().subscribe(cloudEvent -> {
            Flux.fromIterable(consumers)
                    .publishOn(CLOUD_EVENT_CONSUMER_SCHEDULER)
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
}
