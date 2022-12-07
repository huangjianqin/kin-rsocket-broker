package org.kin.rsocket.core.event;

import io.cloudevents.CloudEvent;
import org.kin.framework.Closeable;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
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
public final class CloudEventBus implements Closeable {
    public static final CloudEventBus INSTANCE = new CloudEventBus();

    /** 接受cloud event的flux */
    public final Sinks.Many<CloudEvent> cloudEventSink = Sinks.many().multicast().onBackpressureBuffer();
    /** cloud event consumers */
    private final List<CloudEventConsumer> consumers = new CopyOnWriteArrayList<>();
    /** event topic processor subscribe disposable */
    private final Disposable disposable;
    /**
     * elastic scheduler处理cloud event消费逻辑,
     * 主要是因为app刷新配置时需要阻塞请求(http)配置信息并且spring的加载配置也不支持异步
     */
    private static final Scheduler CLOUD_EVENT_CONSUMER_SCHEDULER = Schedulers.newBoundedElastic(3, Integer.MAX_VALUE, "cloudEventConsumer");

    public CloudEventBus() {
        disposable = cloudEventSink.asFlux().subscribe(cloudEvent ->
                Flux.fromIterable(consumers)
                        .publishOn(CLOUD_EVENT_CONSUMER_SCHEDULER)
                        .filter(consumer -> consumer.shouldAccept(cloudEvent))
                        .flatMap(consumer -> consumer.consume(cloudEvent))
                        .subscribe());
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

    /**
     * '投递' cloud event, 即将cloud event分派出去交给{@link CloudEventConsumer}处理
     */
    public void postCloudEvent(CloudEvent cloudEvent) {
        cloudEventSink.tryEmitNext(cloudEvent);
    }

    @Override
    public void close() {
        disposable.dispose();
    }
}
