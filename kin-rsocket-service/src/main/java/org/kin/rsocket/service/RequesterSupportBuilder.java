package org.kin.rsocket.service;

import io.rsocket.SocketAcceptor;
import io.rsocket.plugins.RSocketInterceptor;
import org.kin.rsocket.core.ReactiveServiceRegistry;
import org.kin.rsocket.core.RequesterSupport;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;

/**
 * @author huangjianqin
 * @date 2021/3/28
 */
public final class RequesterSupportBuilder {
    private RequesterSupport requesterSupport;
    /** requester interceptors */
    private List<RSocketInterceptor> requesterInterceptors = new ArrayList<>();
    /** responder interceptors */
    private List<RSocketInterceptor> responderInterceptors = new ArrayList<>();

    private RequesterSupportBuilder() {
    }

    public static RequesterSupportBuilder builder(RSocketServiceProperties config,
                                                  Environment env,
                                                  ReactiveServiceRegistry serviceRegistry,
                                                  SocketAcceptor socketAcceptor) {
        return builder(new RequesterSupportImpl(
                config,
                env.getProperty("spring.application.name", env.getProperty("application.name")),
                serviceRegistry, socketAcceptor));
    }


    public static RequesterSupportBuilder builder(RequesterSupport requesterSupport) {
        RequesterSupportBuilder builder = new RequesterSupportBuilder();
        builder.requesterSupport = requesterSupport;
        return builder;
    }

    public RequesterSupportBuilder addResponderInterceptor(RSocketInterceptor interceptor) {
        this.responderInterceptors.add(interceptor);
        return this;
    }

    public RequesterSupportBuilder addRequesterInterceptor(RSocketInterceptor interceptor) {
        this.requesterInterceptors.add(interceptor);
        return this;
    }

    public RequesterSupport build() {
        if (!this.responderInterceptors.isEmpty()) {
            this.requesterSupport.responderInterceptors().addAll(responderInterceptors);
        }
        if (!this.requesterInterceptors.isEmpty()) {
            this.requesterSupport.requesterInterceptors().addAll(requesterInterceptors);
        }
        return this.requesterSupport;
    }
}
