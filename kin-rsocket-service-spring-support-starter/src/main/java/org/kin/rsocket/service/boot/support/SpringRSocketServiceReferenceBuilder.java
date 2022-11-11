package org.kin.rsocket.service.boot.support;

import com.google.common.base.Preconditions;
import org.kin.framework.utils.StringUtils;
import org.springframework.messaging.rsocket.RSocketRequester;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * @author huangjianqin
 * @date 2021/8/22
 */
public final class SpringRSocketServiceReferenceBuilder<T> {
    /** 服务名 */
    private String service;
    /** 服务接口 */
    private Class<T> serviceInterface;
    /** 底层remote requester */
    private RSocketRequester rsocketRequester;
    /** remote request超时时间 */
    private Duration timeout = Duration.ofSeconds(3);

    public static <T> SpringRSocketServiceReferenceBuilder<T> reference(RSocketRequester rsocketRequester) {
        SpringRSocketServiceReferenceBuilder<T> builder = new SpringRSocketServiceReferenceBuilder<>();
        builder.rsocketRequester = rsocketRequester;
        return builder;
    }

    public static <T> SpringRSocketServiceReferenceBuilder<T> reference(RSocketRequester rsocketRequester, Class<T> serviceInterface) {
        SpringRSocketServiceReferenceBuilder<T> builder = new SpringRSocketServiceReferenceBuilder<>();
        builder.rsocketRequester = rsocketRequester;
        builder.service(serviceInterface);
        return builder;
    }

    public SpringRSocketServiceReferenceBuilder<T> service(Class<T> serviceInterface) {
        this.serviceInterface = serviceInterface;
        return this;
    }

    public SpringRSocketServiceReferenceBuilder<T> service(String service) {
        this.service = service;
        return this;
    }

    public SpringRSocketServiceReferenceBuilder<T> timeout(long time, TimeUnit unit) {
        this.timeout = Duration.ofMillis(unit.toMillis(time));
        return this;
    }

    public SpringRSocketServiceReferenceBuilder<T> timeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    @SuppressWarnings("unchecked")
    public T build() {
        Preconditions.checkNotNull(rsocketRequester, "RSocketRequester instance is null");
        Preconditions.checkNotNull(serviceInterface, "service interface class is null");
        if (StringUtils.isBlank(service)) {
            //默认为服务接口
            this.service = serviceInterface.getName();
        }

        RequesterProxy requesterProxy = new RequesterProxy(rsocketRequester, service, serviceInterface, timeout);
        if (ByteBuddySupport.ENHANCE) {
            return ByteBuddyUtils.build(this.serviceInterface, requesterProxy);
        } else {
            return (T) Proxy.newProxyInstance(
                    serviceInterface.getClassLoader(),
                    new Class[]{serviceInterface},
                    requesterProxy);
        }

    }
}
