package org.kin.rsocket.core;

import reactor.util.context.Context;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 可变 context
 *
 * @author huangjianqin
 * @date 2021/3/26
 */
public class MutableContext implements Context {
    /** context内容 */
    private final HashMap<Object, Object> holder = new HashMap<>(4);

    @SuppressWarnings("unchecked")
    @Override
    public <T> T get(@Nonnull Object key) {
        return (T) holder.get(key);
    }

    @Nonnull
    @Override
    public boolean hasKey(@Nonnull Object key) {
        return holder.containsKey(key);
    }

    @Nonnull
    @Override
    public Context put(@Nonnull Object key, @Nonnull Object value) {
        holder.put(key, value);
        return this;
    }

    @Nonnull
    @Override
    public Context delete(@Nonnull Object key) {
        holder.remove(key);
        return this;
    }

    @Override
    public int size() {
        return holder.size();
    }

    @Nonnull
    @Override
    public Stream<Map.Entry<Object, Object>> stream() {
        return holder.entrySet().stream();
    }
}
