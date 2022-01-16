package org.kin.rsocket.springcloud.gateway.grpc;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.TraceContext;
import com.google.protobuf.GeneratedMessageV3;
import io.micrometer.core.instrument.Metrics;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.util.ByteBufPayload;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import org.kin.framework.utils.ExceptionUtils;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.core.*;
import org.kin.rsocket.core.metadata.TracingMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

/**
 * reactive grpc 服务方法调用拦截处理
 *
 * @author huangjianqin
 * @date 2022/1/9
 */
public final class ReactiveGrpcCallInterceptor {
    private static final Logger log = LoggerFactory.getLogger(ReactiveGrpcCallInterceptor.class);
    /** group */
    private String group;
    /** service name */
    private String service;
    /** version */
    private String version;
    /** service gsv */
    private String serviceId;
    /** 请求超时 */
    private Duration callTimeout = Duration.ofMillis(3000);
    /**
     * endpoint
     * 形式:
     * 1. id:XX
     * 2. uuid:XX
     * 3. ip:XX
     */
    private String endpoint = "";
    /**
     * sticky session
     * 相当于固定session, 指定service首次请求后, 后面请求都是route到该service instance
     * 如果该service instance失效, 重新选择一个sticky service instance
     * 目前仅仅在service mesh校验通过下才允许mark sticky service instance
     */
    private boolean sticky;
    /** 选择一个合适的{@link UpstreamCluster}(可broker可直连)的selector */
    private UpstreamClusterSelector selector;
    /** zipkin */
    private Tracer tracer;

    /** grpc服务方法元数据 */
    private final Map<Method, ReactiveGrpcMethodMetadata> methodMetadataMap = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    @RuntimeType
    public Object intercept(@Origin Method method, @AllArguments Object[] params) {
        if (Object.class.equals(method.getDeclaringClass())) {
            //过滤Object方法
            try {
                return method.invoke(this, params);
            } catch (IllegalAccessException | InvocationTargetException e) {
                ExceptionUtils.throwExt(e);
            }
        }

        if (StringUtils.isBlank(serviceId)) {
            serviceId = ServiceLocator.gsv(group, service, version);
        }
        ReactiveGrpcMethodMetadata methodMetadata;
        if (!methodMetadataMap.containsKey(method)) {
            methodMetadata = new ReactiveGrpcMethodMetadata(method, group, service, version, endpoint, sticky);
            methodMetadataMap.put(method, methodMetadata);
        } else {
            methodMetadata = methodMetadataMap.get(method);
        }

        RSocket requester = selector.select(serviceId);
        if (methodMetadata.getRpcType().equals(ReactiveGrpcMethodMetadata.UNARY)) {
            //request response
            Mono<GeneratedMessageV3> monoParam = (Mono<GeneratedMessageV3>) params[0];
            return monoParam
                    .map(param -> Unpooled.wrappedBuffer(param.toByteArray()))
                    .flatMap(paramBodyBytes ->
                            rsocketRpc(requester, methodMetadata, paramBodyBytes)
                    );
        } else if (methodMetadata.getRpcType().equals(ReactiveGrpcMethodMetadata.SERVER_STREAMING)) {
            //request stream
            Mono<GeneratedMessageV3> monoParam = (Mono<GeneratedMessageV3>) params[0];
            return monoParam
                    .map(param -> Unpooled.wrappedBuffer(param.toByteArray()))
                    .flatMapMany(paramBodyBytes ->
                            rsocketStream(requester, methodMetadata, paramBodyBytes));
        } else if (methodMetadata.getRpcType().equals(ReactiveGrpcMethodMetadata.CLIENT_STREAMING) ||
                methodMetadata.getRpcType().equals(ReactiveGrpcMethodMetadata.BIDIRECTIONAL_STREAMING)) {
            //client streaming or bidirectional streaming, rsocket request channel

            //param flux
            Flux<Payload> paramsPayloadFlux = ((Flux<GeneratedMessageV3>) params[0])
                    .map(param -> ByteBufPayload.create(Unpooled.wrappedBuffer(param.toByteArray()), PayloadUtils.getCompositeMetaDataWithEncoding()));

            Flux<?> responseFlux = rsocketChannel(requester, methodMetadata, paramsPayloadFlux);
            if (methodMetadata.getRpcType().equals(ReactiveGrpcMethodMetadata.CLIENT_STREAMING)) {
                //return one
                return responseFlux.last();
            } else {
                //return many
                return responseFlux;
            }
        }
        return Mono.error(new ReactiveMethodInvokeException("incorrect rpc type for grpc"));
    }

    /**
     * rsocket requestResponse
     */
    @SuppressWarnings("unchecked")
    private <T> Mono<T> rsocketRpc(RSocket rsocket, ReactiveGrpcMethodMetadata methodMetadata, ByteBuf paramBodyBytes) {
        if (Objects.nonNull(tracer)) {
            return Mono.deferContextual(context -> {
                TraceContext traceContext = context.getOrDefault(TraceContext.class, null);
                if (traceContext != null) {
                    CompositeByteBuf newCompositeMetadata = new CompositeByteBuf(PooledByteBufAllocator.DEFAULT, true,
                            2, methodMetadata.getCompositeMetadataByteBuf(), TracingMetadata.zipkin(traceContext).getContent());
                    Span span = tracer.newChild(traceContext);
                    return (Mono<T>) rsocketRpc0(rsocket, methodMetadata, newCompositeMetadata, paramBodyBytes)
                            .doOnError(span::error)
                            .doOnSuccess(payload -> span.finish());
                } else {
                    return rsocketRpc0(rsocket, methodMetadata, methodMetadata.getCompositeMetadataByteBuf(), paramBodyBytes);
                }
            });
        }
        return rsocketRpc0(rsocket, methodMetadata, methodMetadata.getCompositeMetadataByteBuf(), paramBodyBytes);
    }

    /**
     * rsocket requestResponse
     */
    @SuppressWarnings({"ConstantConditions", "unchecked"})
    private <T> Mono<T> rsocketRpc0(RSocket rsocket, ReactiveGrpcMethodMetadata methodMetadata,
                                    ByteBuf compositeMetadataBytes, ByteBuf paramBodyBytes) {
        Class<T> responseClass = (Class<T>) methodMetadata.getInferredClassForReturn();
        return rsocket.requestResponse(ByteBufPayload.create(paramBodyBytes, compositeMetadataBytes))
                .name(methodMetadata.getMethodName())
                .metrics()
                .timeout(callTimeout)
                .doOnError(TimeoutException.class,
                        e -> {
                            metricsTimeout(methodMetadata);
                            log.error(String.format("Timeout to call %s in %s seconds", methodMetadata.getMethodName(), callTimeout), e);
                        })
                .handle((payload, sink) -> {
                    try {
                        sink.next(PayloadUtils.payloadToResponseObject(payload, responseClass));
                    } catch (Exception e) {
                        sink.error(e);
                    }
                });
    }

    /**
     * rsocket requestStream
     */
    @SuppressWarnings("unchecked")
    private <T> Flux<T> rsocketStream(RSocket rsocket, ReactiveGrpcMethodMetadata methodMetadata, ByteBuf paramBodyBytes) {
        if (Objects.nonNull(tracer)) {
            return Flux.deferContextual(context -> {
                TraceContext traceContext = context.getOrDefault(TraceContext.class, null);
                if (traceContext != null) {
                    CompositeByteBuf newCompositeMetadata = new CompositeByteBuf(PooledByteBufAllocator.DEFAULT, true,
                            2, methodMetadata.getCompositeMetadataByteBuf(), TracingMetadata.zipkin(traceContext).getContent());
                    Span span = tracer.newChild(traceContext);
                    return (Flux<T>) rsocketStream0(rsocket, methodMetadata, newCompositeMetadata, paramBodyBytes)
                            .doOnError(span::error)
                            .doOnComplete(span::finish);
                } else {
                    return rsocketStream0(rsocket, methodMetadata, methodMetadata.getCompositeMetadataByteBuf(), paramBodyBytes);
                }
            });
        }
        return rsocketStream0(rsocket, methodMetadata, methodMetadata.getCompositeMetadataByteBuf(), paramBodyBytes);
    }

    /**
     * rsocket requestStream
     */
    @SuppressWarnings({"unchecked", "ConstantConditions"})
    private <T> Flux<T> rsocketStream0(RSocket rsocket, ReactiveGrpcMethodMetadata methodMetadata,
                                       ByteBuf compositeMetadataBytes, ByteBuf paramBodyBytes) {
        Class<T> responseClass = (Class<T>) methodMetadata.getInferredClassForReturn();
        return rsocket.requestStream(ByteBufPayload.create(paramBodyBytes, compositeMetadataBytes))
                .name(methodMetadata.getMethodName())
                .metrics()
                .timeout(callTimeout)
                .doOnError(TimeoutException.class,
                        e -> {
                            metricsTimeout(methodMetadata);
                            log.error(String.format("Timeout to call %s in %s seconds", methodMetadata.getMethodName(), callTimeout), e);
                        })
                .handle((payload, sink) -> {
                    try {
                        sink.next(PayloadUtils.payloadToResponseObject(payload, responseClass));
                    } catch (Exception e) {
                        sink.error(e);
                    }
                });
    }

    /**
     * rsocket requestChannel
     */
    @SuppressWarnings("unchecked")
    private <T> Flux<T> rsocketChannel(RSocket rsocket, ReactiveGrpcMethodMetadata methodMetadata, Flux<Payload> paramsPayloadFlux) {
        if (Objects.nonNull(tracer)) {
            return Flux.deferContextual(context -> {
                TraceContext traceContext = context.getOrDefault(TraceContext.class, null);
                if (traceContext != null) {
                    CompositeByteBuf newCompositeMetadata = new CompositeByteBuf(PooledByteBufAllocator.DEFAULT, true,
                            2, methodMetadata.getCompositeMetadataByteBuf(), TracingMetadata.zipkin(traceContext).getContent());
                    Span span = tracer.newChild(traceContext);
                    return (Flux<T>) rsocketChannel0(rsocket, methodMetadata, newCompositeMetadata, paramsPayloadFlux)
                            .doOnError(span::error)
                            .doOnComplete(span::finish);
                } else {
                    return rsocketChannel0(rsocket, methodMetadata, methodMetadata.getCompositeMetadataByteBuf(), paramsPayloadFlux);
                }
            });
        }
        return rsocketChannel0(rsocket, methodMetadata, methodMetadata.getCompositeMetadataByteBuf(), paramsPayloadFlux);
    }

    /**
     * rsocket requestChannel
     */
    @SuppressWarnings({"unchecked", "ConstantConditions"})
    private <T> Flux<T> rsocketChannel0(RSocket rsocket, ReactiveGrpcMethodMetadata methodMetadata,
                                        ByteBuf compositeMetadataBytes, Flux<Payload> paramsPayloadFlux) {
        Class<T> responseClass = (Class<T>) methodMetadata.getInferredClassForReturn();
        //empty content signal
        Payload routePayload = ByteBufPayload.create(Unpooled.EMPTY_BUFFER, compositeMetadataBytes);
        return rsocket.requestChannel(paramsPayloadFlux.startWith(routePayload))
                .handle((payload, sink) -> {
                    try {
                        sink.next(PayloadUtils.payloadToResponseObject(payload, responseClass));
                    } catch (Exception e) {
                        sink.error(e);
                    }
                });
    }

    private void metricsTimeout(ReactiveGrpcMethodMetadata methodMetadata) {
        Metrics.counter(MetricsNames.RSOCKET_TIMEOUT_ERROR_COUNT, methodMetadata.getMetricsTags()).increment();
    }

    //lazy init field value
    //setter && getter
    String getGroup() {
        return group;
    }

    void setGroup(String group) {
        this.group = group;
    }

    String getService() {
        return service;
    }

    void setService(String service) {
        this.service = service;
    }

    String getVersion() {
        return version;
    }

    void setVersion(String version) {
        this.version = version;
    }

    void updateServiceId() {
        this.serviceId = ServiceLocator.gsv(group, service, version);
    }

    Duration getCallTimeout() {
        return callTimeout;
    }

    void setCallTimeout(Duration callTimeout) {
        this.callTimeout = callTimeout;
    }

    String getEndpoint() {
        return endpoint;
    }

    void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    boolean isSticky() {
        return sticky;
    }

    void setSticky(boolean sticky) {
        this.sticky = sticky;
    }

    UpstreamClusterSelector getSelector() {
        return selector;
    }

    void setSelector(UpstreamClusterSelector selector) {
        this.selector = selector;
    }

    Tracer getTracer() {
        return tracer;
    }

    void setTracing(Tracing tracing) {
        if (Objects.isNull(tracing)) {
            return;
        }
        this.tracer = tracing.tracer();
    }
}
