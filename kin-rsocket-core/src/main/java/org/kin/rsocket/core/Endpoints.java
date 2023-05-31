package org.kin.rsocket.core;

/**
 * @author huangjianqin
 * @date 2023/3/16
 */
public final class Endpoints {
    private Endpoints() {
    }

    public static final String SEPARATOR = ":";

    /**
     * 应用实例id, 即hashcode(应用uuid)
     */
    public static final String INSTANCE_ID = "instanceId" + SEPARATOR;
    /**
     * 应用uuid
     */
    public static final String UUID = "uuid" + SEPARATOR;
    /**
     * 应用ip
     */
    public static final String IP = "uuid" + SEPARATOR;
}
