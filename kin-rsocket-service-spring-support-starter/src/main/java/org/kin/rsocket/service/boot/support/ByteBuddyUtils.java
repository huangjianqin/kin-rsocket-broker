package org.kin.rsocket.service.boot.support;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import org.kin.framework.utils.ExceptionUtils;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author huangjianqin
 * @date 2021/3/27
 */
final class ByteBuddyUtils {
    /** key -> rsocket service interface, value -> 对应的proxy class */
    private static final Map<Class<?>, Class<?>> SERVICE_INTERFACE_CLASS_2_PROXY_CLASS = new ConcurrentHashMap<>();

    private ByteBuddyUtils() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T build(Class<T> serviceInterface, Object proxy) {
        Class<T> proxyClass = (Class<T>) SERVICE_INTERFACE_CLASS_2_PROXY_CLASS.get(serviceInterface);
        if (Objects.isNull(proxyClass)) {
            proxyClass = (Class<T>) new ByteBuddy(ClassFileVersion.JAVA_V8)
                    .subclass(serviceInterface)
                    .name(serviceInterface.getSimpleName() + "RSocketStub")
                    .method(ElementMatchers.not(ElementMatchers.isDefaultMethod()))
                    .intercept(MethodDelegation.to(proxy))
                    .make()
                    .load(serviceInterface.getClassLoader())
                    .getLoaded();
            SERVICE_INTERFACE_CLASS_2_PROXY_CLASS.put(serviceInterface, proxyClass);
        }

        try {
            return proxyClass.newInstance();
        } catch (Exception e) {
            ExceptionUtils.throwExt(e);
        }

        return null;
    }
}
