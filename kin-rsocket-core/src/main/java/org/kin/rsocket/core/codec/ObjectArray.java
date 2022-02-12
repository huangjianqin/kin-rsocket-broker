package org.kin.rsocket.core.codec;

import java.io.Serializable;

/**
 * 对Object[]封装, 以便序列化
 *
 * @author huangjianqin
 * @date 2022/2/12
 */
public final class ObjectArray implements Serializable {
    private static final long serialVersionUID = 2016644867977444578L;

    private Object[] objects;

    public ObjectArray() {
    }

    public ObjectArray(Object[] objects) {
        this.objects = objects;
    }

    //setter && getter
    public Object[] getObjects() {
        return objects;
    }

    public void setObjects(Object[] objects) {
        this.objects = objects;
    }
}
