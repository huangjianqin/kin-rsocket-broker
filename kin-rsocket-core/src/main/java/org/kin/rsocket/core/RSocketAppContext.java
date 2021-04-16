package org.kin.rsocket.core;

import org.kin.framework.utils.KinServiceLoader;
import org.kin.rsocket.core.event.CloudEventData;
import reactor.core.publisher.Sinks;

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
    /** spi loader */
    public static final KinServiceLoader LOADER = KinServiceLoader.load();
    /** 接受cloud event的flux */
    public static final Sinks.Many<CloudEventData<?>> CLOUD_EVENT_SINK = Sinks.many().multicast().onBackpressureBuffer();

    /** 是否支持字节码增强 */
    public static final boolean ENHANCE;
    /** spring web port */
    public static int webPort = 0;
    /** spring actuator port */
    public static int managementPort = 0;
    /** key -> rsocket port, value -> rsocket schema */
    public static Map<Integer, String> rsocketPorts;

    static {
        Class<?> byteBuddyClass = null;
        try {
            byteBuddyClass = Class.forName("net.bytebuddy.ByteBuddy");
        } catch (Exception e) {
            //ignore
        }

        if (Objects.nonNull(byteBuddyClass)) {
            ENHANCE = true;
        } else {
            ENHANCE = false;
        }
    }
}
