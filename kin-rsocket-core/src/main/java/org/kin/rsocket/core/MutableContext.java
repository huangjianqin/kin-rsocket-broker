package org.kin.rsocket.core;

import reactor.util.context.Context;

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
    public <T> T get(Object key) {
        return (T) holder.get(key);
    }

    @Override
    public boolean hasKey(Object key) {
        return holder.containsKey(key);
    }

    @Override
    public Context put(Object key, Object value) {
        holder.put(key, value);
        return this;
    }

    @Override
    public Context delete(Object key) {
        holder.remove(key);
        return this;
    }

    @Override
    public int size() {
        return holder.size();
    }

    @Override
    public Stream<Map.Entry<Object, Object>> stream() {
        return holder.entrySet().stream();
    }
}
