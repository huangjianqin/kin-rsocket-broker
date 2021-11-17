package org.kin.spring.rsocket.support;

import java.util.Objects;

/**
 * @author huangjianqin
 * @date 2021/8/23
 */
final class ByteBuddySupport {
    /** 是否支持字节码增强 */
    public final static boolean ENHANCE;

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
