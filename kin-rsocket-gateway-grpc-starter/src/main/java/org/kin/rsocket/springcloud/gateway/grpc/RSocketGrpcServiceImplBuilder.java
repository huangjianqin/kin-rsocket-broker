package org.kin.rsocket.springcloud.gateway.grpc;

import brave.Tracing;
import io.grpc.BindableService;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.implementation.MethodDelegation;
import org.kin.framework.utils.ExceptionUtils;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.UpstreamCluster;
import org.kin.rsocket.core.UpstreamClusterSelector;
import org.kin.rsocket.service.RSocketServiceReference;
import org.kin.rsocket.service.UpstreamClusterManager;
import org.lognet.springboot.grpc.GRpcService;
import org.springframework.core.annotation.AnnotationAttributes;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static net.bytebuddy.matcher.ElementMatchers.*;

/**
 * grpc service rsocket implementation builder
 *
 * @author huangjianqin
 * @date 2022/1/9
 */
public final class RSocketGrpcServiceImplBuilder<T extends BindableService> {
    /** grpc service */
    private final Class<T> serviceStub;
    /** grpc调用拦截 */
    private final ReactiveGrpcCallInterceptor interceptor = new ReactiveGrpcCallInterceptor();
    /** consumer是否开启p2p */
    private boolean p2p;

    public RSocketGrpcServiceImplBuilder(Class<T> serviceStub) {
        this.serviceStub = serviceStub;
    }

    public static <T extends BindableService> RSocketGrpcServiceImplBuilder<T> stub(Class<T> serviceStub) {
        return new RSocketGrpcServiceImplBuilder<T>(serviceStub);
    }

    /**
     * 指定service interface class和{@link RSocketServiceReference}属性生成builder实例
     */
    public static <T extends BindableService> RSocketGrpcServiceImplBuilder<T> stub(Class<T> serviceInterface, AnnotationAttributes annoAttrs) {
        RSocketGrpcServiceImplBuilder<T> builder = new RSocketGrpcServiceImplBuilder<>(serviceInterface);
        builder.service(serviceInterface.getName());

        String serviceName = annoAttrs.getString("name");
        if (StringUtils.isNotBlank(serviceName)) {
            builder.service(serviceName);
        }

        String group = annoAttrs.getString("group");
        if (StringUtils.isNotBlank(group)) {
            builder.group(group);
        }

        String version = annoAttrs.getString("version");
        if (StringUtils.isNotBlank(version)) {
            builder.version(version);
        }

        int callTimeout = annoAttrs.getNumber("callTimeout");
        if (callTimeout > 0) {
            builder.callTimeout(callTimeout);
        }

        String endpoint = annoAttrs.getString("endpoint");
        if (StringUtils.isNotBlank(endpoint)) {
            builder.endpoint(endpoint);
        }

        boolean sticky = annoAttrs.getBoolean("sticky");
        if (sticky) {
            builder.sticky();
        }

        boolean p2p = annoAttrs.getBoolean("p2p");
        if (p2p) {
            builder.p2p();
        }

        return builder;
    }

    /**
     * 选择一个合适的{@link UpstreamCluster}(可broker可直连)的selector
     */
    public RSocketGrpcServiceImplBuilder<T> upstreamClusterManager(UpstreamClusterManager upstreamClusterManager) {
        interceptor.setSelector(upstreamClusterManager);
        return this;
    }

    /**
     * 选择一个合适的{@link UpstreamCluster}(可broker可直连)的selector
     */
    public RSocketGrpcServiceImplBuilder<T> requester(UpstreamClusterSelector selector) {
        interceptor.setSelector(selector);
        return this;
    }

    /**
     * group
     */
    public RSocketGrpcServiceImplBuilder<T> group(String group) {
        interceptor.setGroup(group);
        return this;
    }

    /**
     * service name
     */
    public RSocketGrpcServiceImplBuilder<T> service(String service) {
        interceptor.setService(service);
        return this;
    }

    /**
     * version
     */
    public RSocketGrpcServiceImplBuilder<T> version(String version) {
        interceptor.setVersion(version);
        return this;
    }

    /**
     * 请求超时
     */
    public RSocketGrpcServiceImplBuilder<T> callTimeout(int millis) {
        interceptor.setCallTimeout(Duration.ofMillis(millis));
        return this;
    }

    /**
     * endpoint
     */
    public RSocketGrpcServiceImplBuilder<T> endpoint(String endpoint) {
        interceptor.setEndpoint(endpoint);
        return this;
    }

    /**
     * sticky session
     */
    public RSocketGrpcServiceImplBuilder<T> sticky() {
        interceptor.setSticky(true);
        return this;
    }

    /**
     * consumer是否开启p2p
     */
    public RSocketGrpcServiceImplBuilder<T> p2p() {
        this.p2p = true;
        return this;
    }

    /**
     * 开启zipkin tracing
     */
    public RSocketGrpcServiceImplBuilder<T> tracing(Tracing tracing) {
        this.interceptor.setTracing(tracing);
        return this;
    }

    /**
     * 合法性检查
     */
    private void check(ServiceLocator serviceLocator) {
        if (interceptor.getSelector() instanceof UpstreamCluster) {
            UpstreamCluster upstreamCluster = (UpstreamCluster) interceptor.getSelector();
            if (!serviceLocator.getGsv().equals(upstreamCluster.getServiceId())) {
                //检查构建的服务reference service gsv与builder指定的gsv是否一致
                throw new IllegalStateException("UpstreamCluster's service gsv must be match GrpcRSocketServiceImplBuilder's service gsv");
            }
        }
    }

    @SuppressWarnings("unchecked")
    public T build() {
        Class<T> dynamicType = (Class<T>) new ByteBuddy()
                .subclass(serviceStub)
                .name(serviceStub.getName() + "RSocketImpl")
                .annotateType(AnnotationDescription.Builder.ofType(GRpcService.class).build())
                .method(returns(target -> target.isAssignableFrom(Mono.class) || target.isAssignableFrom(Flux.class))
                        .and(takesArguments(1))
                        .and(hasParameters(whereAny(hasType(target -> target.isAssignableFrom(Mono.class) || target.isAssignableFrom(Flux.class))))))
                .intercept(MethodDelegation.to(interceptor))
                .make()
                .load(getClass().getClassLoader())
                .getLoaded();
        T instance = null;
        try {
            instance = dynamicType.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            ExceptionUtils.throwExt(e);
        }

        String service = interceptor.getService();
        if (StringUtils.isBlank(service)) {
            //如果没有设置service name, 则使用grpc service name
            interceptor.setService(instance.bindService().getServiceDescriptor().getName());
        }
        interceptor.updateServiceId();

        ServiceLocator serviceLocator = ServiceLocator.of(interceptor.getGroup(), service, interceptor.getVersion());
        check(serviceLocator);
        if (interceptor.getSelector() instanceof UpstreamClusterManager && p2p) {
            ((UpstreamClusterManager) interceptor.getSelector()).openP2p(serviceLocator.getGsv());
        }

        return instance;
    }

    //getter
    Class<T> getServiceStub() {
        return serviceStub;
    }

    ReactiveGrpcCallInterceptor getInterceptor() {
        return interceptor;
    }
}
