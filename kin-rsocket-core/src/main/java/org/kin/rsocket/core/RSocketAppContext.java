package org.kin.rsocket.core;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * @author huangjianqin
 * @date 2021/3/23
 */
public class RSocketAppContext {
    /** app uuid */
    public static final String ID = UUID.randomUUID().toString();
    /** 是否支持字节码增强 */
    public static final boolean ENHANCE;
    /** spring web port */
    public static volatile int webPort = 0;
    /** spring actuator port */
    public static volatile int managementPort = 0;
    /** key -> rsocket port, value -> rsocket schema */
    public static volatile Map<Integer, String> rsocketPorts;

    static {
        Class<?> byteBuddyClass = null;
        try {
            byteBuddyClass = Class.forName("net.bytebuddy.ByteBuddy");
        } catch (Exception e) {
            //ignore
        }

        ENHANCE = Objects.nonNull(byteBuddyClass);
    }
}
