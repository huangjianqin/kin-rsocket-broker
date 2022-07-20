package org.kin.rsocket.spring.support;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import org.checkerframework.common.returnsreceiver.qual.This;
import org.kin.framework.utils.CollectionUtils;
import org.kin.framework.utils.ExceptionUtils;
import org.kin.framework.utils.MethodHandleUtils;
import org.springframework.messaging.rsocket.RSocketRequester;
import reactor.core.publisher.Mono;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.rsocket.frame.FrameType.*;

/**
 * @author huangjianqin
 * @date 2021/8/22
 */
public final class RequesterProxy implements InvocationHandler {
    /** reactive方法元数据 */
    private final Map<Method, ReactiveMethodMetadata> methodMetadatas = new ConcurrentHashMap<>();
    /** 底层remote requester */
    private final RSocketRequester rsocketRequester;
    /** service interface */
    private final Class<?> serviceInterface;
    /** 服务名 */
    private final String service;
    /** 请求超时 */
    private final Duration timeout;

    public RequesterProxy(RSocketRequester rsocketRequester, Class<?> serviceInterface, Duration timeout) {
        this(rsocketRequester, serviceInterface.getName(), serviceInterface, timeout);
    }

    public RequesterProxy(RSocketRequester rsocketRequester, String service, Class<?> serviceInterface, Duration timeout) {
        this.rsocketRequester = rsocketRequester;
        this.serviceInterface = serviceInterface;
        this.service = service;
        this.timeout = timeout;
    }

    @Override
    @RuntimeType
    public Object invoke(@This Object proxy, @Origin Method method, @AllArguments Object[] args) {
        if (Object.class.equals(method.getDeclaringClass())) {
            //过滤Object方法
            try {
                method.invoke(this, args);
            } catch (IllegalAccessException | InvocationTargetException e) {
                ExceptionUtils.throwExt(e);
            }
        }

        if (!ByteBuddySupport.ENHANCE && method.isDefault()) {
            //jdk代理下, 如果是调用default方法, 直接使用句柄掉漆
            try {
                return MethodHandleUtils.getInterfaceDefaultMethodHandle(method, serviceInterface).bindTo(proxy).invokeWithArguments(args);
            } catch (Throwable throwable) {
                ExceptionUtils.throwExt(throwable);
            }
        }

        //spring messaging版本的route key
        String routeKey = service + Separators.SERVICE_HANDLER + method.getName();
        ReactiveMethodMetadata metadata = methodMetadatas.get(method);
        if (metadata == null) {
            metadata = new ReactiveMethodMetadata(method);
            methodMetadatas.putIfAbsent(method, metadata);
        }

        RSocketRequester.RetrieveSpec retrieveSpec;
        if (CollectionUtils.isNonEmpty(args)) {
            retrieveSpec = rsocketRequester.route(routeKey).data(args[0]);
        } else {
            retrieveSpec = rsocketRequester.route(routeKey).data(Mono.empty());
        }
        if (metadata.getFrameType() == REQUEST_RESPONSE) {
            //request response
            return retrieveSpec.retrieveMono(metadata.getInferredClassForReturn()).timeout(timeout);
            //request stream || request channel
        } else if (metadata.getFrameType() == REQUEST_STREAM || metadata.getFrameType() == REQUEST_CHANNEL) {
            return retrieveSpec.retrieveFlux(metadata.getInferredClassForReturn()).timeout(timeout);
        } else {
            //fire forget
            return retrieveSpec.send().timeout(timeout);
        }
    }
}
