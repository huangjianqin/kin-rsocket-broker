package org.kin.rsocket.core.event;

import java.util.List;

/**
 * invalid spring cache event
 *
 * @author huangjianqin
 * @date 2021/3/24
 */
public final class CacheInvalidEvent implements CloudEventSupport {
    private static final long serialVersionUID = 779297386423612211L;
    /** cache keys */
    private List<String> keys;

    public static CacheInvalidEvent of(List<String> keys) {
        CacheInvalidEvent inst = new CacheInvalidEvent();
        inst.keys = keys;
        return inst;
    }

    //setter && getter
    public List<String> getKeys() {
        return keys;
    }

    public void setKeys(List<String> keys) {
        this.keys = keys;
    }

    @Override
    public String toString() {
        return "CacheInvalidEvent{" +
                "keys=" + keys +
                '}';
    }
}