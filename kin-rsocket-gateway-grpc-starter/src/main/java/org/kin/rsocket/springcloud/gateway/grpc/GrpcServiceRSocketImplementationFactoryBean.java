package org.kin.rsocket.springcloud.gateway.grpc;

import brave.Tracing;
import io.grpc.BindableService;
import org.kin.rsocket.service.RSocketServiceRequester;
import org.kin.rsocket.springcloud.service.RSocketServiceProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AbstractFactoryBean;

import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import java.util.Objects;

/**
 * @author huangjianqin
 * @date 2022/1/12
 */
public final class GrpcServiceRSocketImplementationFactoryBean<T extends BindableService> extends AbstractFactoryBean<T> {
    @Autowired
    private RSocketServiceRequester requester;
    @Autowired
    private RSocketServiceProperties rsocketServiceProperties;
    /** 缓存rsocket rpc service reference builder, 创建reference后会clear掉 */
    private GrpcServiceRSocketImplementationBuilder<T> builder;
    /** rsocket service 服务reference, 仅仅build一次 */
    private final T reference;
    @Autowired(required = false)
    private Tracing tracing;

    public GrpcServiceRSocketImplementationFactoryBean(Class<T> claxx) {
        if (!BindableService.class.isAssignableFrom(claxx)) {
            throw new IllegalArgumentException(
                    String.format("class '%s' must be extends BindableService", claxx.getName()));
        }

        Class<?> declaringClass = claxx.getDeclaringClass();
        if (Objects.isNull(declaringClass) || !declaringClass.getSimpleName().startsWith("Reactor")) {
            //reactor grpc生成出来的都是以Reactor开头, 故这里写死类名过滤
            throw new IllegalArgumentException(String.format("class '%s' must be generate by reactor-grpc-stub", claxx.getName()));
        }

        builder = GrpcServiceRSocketImplementationBuilder.stub(claxx);
        reference = builder.getInstance();
    }

    @PostConstruct
    public void init() {
        builder.groupIfEmpty(rsocketServiceProperties.getGroup())
                .versionIfEmpty(rsocketServiceProperties.getVersion())
                .callTimeout(rsocketServiceProperties.getTimeout());
        builder.upstreamClusterManager(requester);
        builder.tracing(tracing);
        builder.connect();
        //release
        builder = null;
    }

    @Override
    public Class<?> getObjectType() {
        //马上返回带@GRpcService注解的grpc service stub子类
        return reference.getClass();
    }

    @Nonnull
    @Override
    protected T createInstance() {
        return reference;
    }

    /**
     * 单例
     */
    @Override
    public boolean isSingleton() {
        return true;
    }
}
