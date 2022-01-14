package org.kin.rsocket.springcloud.gateway.grpc;

import brave.Tracing;
import io.grpc.BindableService;
import org.kin.rsocket.service.RSocketServiceRequester;
import org.kin.rsocket.springcloud.service.RSocketServiceProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AbstractFactoryBean;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * @author huangjianqin
 * @date 2022/1/12
 */
public final class RSocketGrpcServiceReferenceFactoryBean<T extends BindableService> extends AbstractFactoryBean<T> {
    @Autowired
    private RSocketServiceRequester requester;
    @Autowired
    private RSocketServiceProperties rsocketServiceProperties;
    /** 缓存rsocket rpc service reference builder, 创建reference后会clear掉 */
    private RSocketGrpcServiceImplBuilder<T> builder;
    /** rsocket service 服务reference, 仅仅build一次 */
    private volatile T reference;
    @Autowired(required = false)
    private Tracing tracing;

    public RSocketGrpcServiceReferenceFactoryBean(RSocketGrpcServiceImplBuilder<T> builder) {
        this.builder = builder;
    }

    @SuppressWarnings("unchecked")
    public RSocketGrpcServiceReferenceFactoryBean(Class<T> claxx) {
        if (!claxx.isInterface()) {
            throw new IllegalArgumentException(
                    String.format("class '%s' must be interface", claxx.getName()));
        }

        if (!BindableService.class.isAssignableFrom(claxx)) {
            throw new IllegalArgumentException(
                    String.format("class '%s' must be extends BindableService", claxx.getName()));
        }

        builder = RSocketGrpcServiceImplBuilder.stub(claxx);
        builder.groupIfEmpty(rsocketServiceProperties.getGroup())
                .versionIfEmpty(rsocketServiceProperties.getVersion())
                .callTimeout(rsocketServiceProperties.getTimeout());
    }

    @Override
    public Class<?> getObjectType() {
        if (Objects.nonNull(builder)) {
            return builder.getServiceStub();
        } else {
            return reference.getClass();
        }
    }

    @Nonnull
    @Override
    protected T createInstance() {
        if (Objects.isNull(reference)) {
            builder.upstreamClusterManager(requester);
            builder.tracing(tracing);
            reference = builder.build();
            //release
            builder = null;
        }

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
