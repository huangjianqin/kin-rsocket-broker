package org.kin.rsocket.core;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * 将一些常见的类实例转换成reactive实例
 *
 * @author huangjianqin
 * @date 2021/3/26
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class ReactiveObjAdapter {
    public static final ReactiveObjAdapter INSTANCE = new ReactiveObjAdapter();

    private ReactiveObjAdapter() {
    }

    /**
     * 转换成{@link Mono}
     */
    public <T> Mono<T> toMono(Object source) {
        if (source instanceof Mono) {
            return (Mono) source;
        } else if (source instanceof Publisher) {
            return Mono.from((Publisher) source);
        } else if (source instanceof CompletableFuture) {
            return Mono.fromFuture((CompletableFuture) source);
        } else if (source instanceof Throwable) {
            return Mono.error((Throwable) source);
        } else {
            return (Mono<T>) Mono.justOrEmpty(source);
        }
    }

    /**
     * 转换成{@link Flux}
     */
    public <T> Flux<T> toFlux(Object source) {
        if (source instanceof Flux) {
            return (Flux) source;
        } else if (source instanceof Mono) {
            return Flux.from((Mono<T>) source);
        } else if (source instanceof Iterable) {
            return Flux.fromIterable((Iterable) source);
        } else if (source instanceof Stream) {
            return Flux.fromStream((Stream) source);
        } else if (source instanceof Publisher) {
            return Flux.from((Publisher) source);
        } else if (source == null) {
            return Flux.empty();
        } else if (source.getClass().isArray()) {
            return Flux.fromArray((T[]) source);
        } else if (source instanceof CompletableFuture) {
            return Flux.from(toMono(source));
        }
        return (Flux<T>) Flux.just(source);
    }

    /**
     * {@link Mono}带上context
     */
    public Object fromPublisher(Mono<?> mono, MutableContext mutableContext) {
        return mono.contextWrite(c -> mutableContext.putAll(c.readOnly()));
    }

    /**
     * {@link Flux}带上context
     */
    public Object fromPublisher(Flux<?> flux, MutableContext mutableContext) {
        return flux.contextWrite(c -> mutableContext.putAll(c.readOnly()));
    }
}
