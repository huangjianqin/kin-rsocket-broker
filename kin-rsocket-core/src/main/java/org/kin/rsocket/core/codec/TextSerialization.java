package org.kin.rsocket.core.codec;

import org.kin.kinrpc.serialization.Serialization;

import java.nio.charset.StandardCharsets;

/**
 * @author huangjianqin
 * @date 2021/3/25
 */
public class TextSerialization implements Serialization {
    @Override
    public byte[] serialize(Object target) {
        return target.toString().getBytes(StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T deserialize(byte[] bytes, Class<T> targetClass) {
        return (T) new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public int type() {
        return 8;
    }
}
