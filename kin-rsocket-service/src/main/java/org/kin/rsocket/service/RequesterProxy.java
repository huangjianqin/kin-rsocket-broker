package org.kin.rsocket.service;

import io.micrometer.core.instrument.Metrics;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import io.rsocket.Payload;
import io.rsocket.frame.FrameType;
import io.rsocket.util.ByteBufPayload;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;
import org.kin.framework.utils.CollectionUtils;
import org.kin.framework.utils.ExceptionUtils;
import org.kin.framework.utils.MethodHandleUtils;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.core.*;
import org.kin.rsocket.core.codec.ObjectCodecs;
import org.kin.rsocket.core.metadata.MessageMimeTypeMetadata;
import org.kin.rsocket.core.metadata.RSocketCompositeMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

/**
 * requester 代理
 * <p>
 * 类定义必须为public, 不然生成出来的代理无法访问到该类
 *
 * @author huangjianqin
 * @date 2021/3/27
 */
public class RequesterProxy implements InvocationHandler {
    private static final Logger log = LoggerFactory.getLogger(RequesterProxy.class);
    /** 选择一个合适的{@link UpstreamCluster}(可broker可直连)的selector */
    protected final UpstreamClusterSelector selector;
    /** service interface */
    protected final Class<?> serviceInterface;
    /** group */
    protected final String group;
    /** service name */
    protected final String service;
    /** service version */
    protected final String version;
    /** service gsv */
    protected final String serviceId;
    /** endpoint of service */
    protected final String endpoint;
    /** sticky session */
    protected final boolean sticky;
    /** encoding type */
    private final URI sourceUri;
    /** 数据编码类型 */
    protected final RSocketMimeType defaultEncodingType;
    /** accept encoding types */
    protected final RSocketMimeType[] defaultAcceptEncodingTypes;
    /** timeout for request/response */
    protected final Duration timeout;
    /** java method metadata map cache for performance */
    protected final Map<Method, ReactiveMethodMetadata> methodMetadataMap = new ConcurrentHashMap<>();

    /**
     * 默认accept的数据编码类型
     */
    public static RSocketMimeType[] defaultAcceptEncodingTypes() {
        return new RSocketMimeType[]{RSocketMimeType.JSON, RSocketMimeType.JAVA_OBJECT, RSocketMimeType.PROTOBUF,
                RSocketMimeType.HESSIAN, RSocketMimeType.AVRO, RSocketMimeType.CBOR,
                RSocketMimeType.TEXT, RSocketMimeType.BINARY};
    }

    public RequesterProxy(RSocketServiceReferenceBuilder<?> builder) {
        selector = builder.getSelector();
        serviceInterface = builder.getServiceInterface();
        if (StringUtils.isBlank(builder.getService())) {
            service = serviceInterface.getName();
        } else {
            service = builder.getService();
        }

        group = builder.getGroup();
        version = builder.getVersion();
        serviceId = ServiceLocator.gsv(group, service, version);
        endpoint = builder.getEndpoint();
        sticky = builder.isSticky();
        sourceUri = builder.getSourceUri();
        defaultEncodingType = builder.getEncodingType();
        RSocketMimeType.checkEncodingMimeType(defaultEncodingType);

        RSocketMimeType[] acceptEncodingTypes = builder.getAcceptEncodingTypes();
        if (CollectionUtils.isNonEmpty(acceptEncodingTypes)) {
            this.defaultAcceptEncodingTypes = defaultAcceptEncodingTypes();
        } else {
            for (RSocketMimeType acceptEncodingType : acceptEncodingTypes) {
                RSocketMimeType.checkEncodingMimeType(acceptEncodingType);
            }
            this.defaultAcceptEncodingTypes = acceptEncodingTypes;
        }
        timeout = builder.getCallTimeout();
    }

    @Override
    @RuntimeType
    public Object invoke(@This Object proxy, @Origin Method method, @AllArguments Object[] args) {
        if (!RSocketAppContext.ENHANCE && method.isDefault()) {
            //jdk代理下, 如果是调用default方法, 直接使用句柄掉漆
            try {
                return MethodHandleUtils.getInterfaceDefaultMethodHandle(method, serviceInterface).bindTo(proxy).invokeWithArguments(args);
            } catch (Throwable throwable) {
                ExceptionUtils.throwExt(throwable);
            }
        }

        if (method.getDeclaringClass().equals(Object.class)) {
            //过滤Object方法
            try {
                return method.invoke(this, args);
            } catch (IllegalAccessException | InvocationTargetException e) {
                ExceptionUtils.throwExt(e);
            }
        }

        ReactiveMethodMetadata methodMetadata = methodMetadataMap.get(method);
        if (Objects.isNull(methodMetadata)) {
            //lazy init method metadata
            methodMetadata = new ReactiveMethodMetadata(group, service, version,
                    method, defaultEncodingType, defaultAcceptEncodingTypes, endpoint, sticky, sourceUri);
            methodMetadataMap.put(method, methodMetadata);
        }
        MutableContext mutableContext = new MutableContext();
        mutableContext.put(ReactiveMethodMetadata.class, methodMetadata);

        if (methodMetadata.getFrameType() == FrameType.REQUEST_CHANNEL) {
            //request channel
            metrics(methodMetadata);
            ByteBuf routeBytes;
            Flux<Object> paramBodys;
            if (args.length == 1) {
                //1 param
                routeBytes = Unpooled.EMPTY_BUFFER;
                paramBodys = ReactiveObjAdapter.INSTANCE.toFlux(args[0]);
            } else {
                //2 params
                routeBytes = ObjectCodecs.INSTANCE.encodeResult(args[0], methodMetadata.getDataEncodingType());
                paramBodys = ReactiveObjAdapter.INSTANCE.toFlux(args[1]);
            }

            //handle return
            ReactiveMethodMetadata finalMethodMetadata1 = methodMetadata;
            Flux<Object> result = requestChannel(methodMetadata, methodMetadata.getCompositeMetadataBytes(), routeBytes, paramBodys)
                    .concatMap(payload -> {
                        try {
                            RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.from(payload.metadata());
                            return Mono.justOrEmpty(ObjectCodecs.INSTANCE.decodeResult(
                                    extractPayloadDataMimeType(compositeMetadata, finalMethodMetadata1.getAcceptEncodingTypes()[0]),
                                    payload.data(),
                                    finalMethodMetadata1.getInferredClassForReturn()));
                        } catch (Exception e) {
                            return Flux.error(e);
                        } finally {
                            ReferenceCountUtil.safeRelease(payload);
                        }
                    }).contextWrite(c -> mutableContext.putAll(c.readOnly()));
            if (methodMetadata.isMonoChannel()) {
                return result.last();
            } else {
                return result;
            }
        } else {
            //body content
            ByteBuf paramBodyBytes = ObjectCodecs.INSTANCE.encodeParams(args, methodMetadata.getDataEncodingType());
            if (methodMetadata.getFrameType() == FrameType.REQUEST_RESPONSE) {
                //request response
                metrics(methodMetadata);
                ReactiveMethodMetadata finalMethodMetadata = methodMetadata;
                //handle return
                Mono<Object> result = requestResponse(methodMetadata, methodMetadata.getCompositeMetadataBytes(), paramBodyBytes)
                        .handle((payload, sink) -> {
                            try {
                                RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.from(payload.metadata());
                                Object obj = ObjectCodecs.INSTANCE.decodeResult(
                                        extractPayloadDataMimeType(compositeMetadata, finalMethodMetadata.getAcceptEncodingTypes()[0]),
                                        payload.data(),
                                        finalMethodMetadata.getInferredClassForReturn());
                                if (obj != null) {
                                    sink.next(obj);
                                }
                                sink.complete();
                            } catch (Exception e) {
                                sink.error(e);
                            } finally {
                                ReferenceCountUtil.safeRelease(payload);
                            }
                        });
                return ReactiveObjAdapter.INSTANCE.fromPublisher(result, mutableContext);
            } else if (methodMetadata.getFrameType() == FrameType.REQUEST_FNF) {
                //request and forget
                metrics(methodMetadata);
                Mono<Void> result = fireAndForget(methodMetadata, methodMetadata.getCompositeMetadataBytes(), paramBodyBytes);
                if (methodMetadata.isReturnVoid()) {
                    //返回void
                    result.subscribe();
                    return null;
                } else {
                    return result;
                }
            } else if (methodMetadata.getFrameType() == FrameType.REQUEST_STREAM) {
                //request stream
                metrics(methodMetadata);
                ReactiveMethodMetadata finalMethodMetadata = methodMetadata;
                Flux<Object> result = requestStream(methodMetadata, methodMetadata.getCompositeMetadataBytes(), paramBodyBytes)
                        .concatMap((payload) -> {
                            try {
                                RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.from(payload.metadata());
                                return Mono.justOrEmpty(ObjectCodecs.INSTANCE.decodeResult(
                                        extractPayloadDataMimeType(compositeMetadata, finalMethodMetadata.getAcceptEncodingTypes()[0]),
                                        payload.data(),
                                        finalMethodMetadata.getInferredClassForReturn()));
                            } catch (Exception e) {
                                return Mono.error(e);
                            } finally {
                                ReferenceCountUtil.safeRelease(payload);
                            }
                        });
                return ReactiveObjAdapter.INSTANCE.fromPublisher(result, mutableContext);
            } else {
                ReferenceCountUtil.safeRelease(paramBodyBytes);
                return Mono.error(new Exception("unknown RSocket Frame type: " + methodMetadata.getFrameType().name()));
            }
        }
    }

    /**
     * requestResponse请求封装, 用于子类扩展
     */
    protected Mono<Payload> requestResponse(ReactiveMethodMetadata methodMetadata, ByteBuf compositeMetadataBytes, ByteBuf paramBodyBytes) {
        return selector.select(serviceId)
                .requestResponse(ByteBufPayload.create(paramBodyBytes, compositeMetadataBytes))
                .name(methodMetadata.getHandlerIdStr())
                .metrics()
                .timeout(timeout)
                .doOnError(t -> handleCallError(t, methodMetadata));
    }

    /**
     * fireAndForget请求封装, 用于子类扩展
     */
    protected Mono<Void> fireAndForget(ReactiveMethodMetadata methodMetadata, ByteBuf compositeMetadataBytes, ByteBuf paramBodyBytes) {
        return selector.select(serviceId)
                .fireAndForget(ByteBufPayload.create(paramBodyBytes, compositeMetadataBytes))
                .name(methodMetadata.getHandlerIdStr())
                .metrics()
                .timeout(timeout)
                .doOnError(t -> handleCallError(t, methodMetadata));
    }

    /**
     * requestStream请求封装, 用于子类扩展
     */
    protected Flux<Payload> requestStream(ReactiveMethodMetadata methodMetadata, ByteBuf compositeMetadataBytes, ByteBuf paramBodyBytes) {
        return selector.select(serviceId)
                .requestStream(ByteBufPayload.create(paramBodyBytes, compositeMetadataBytes))
                .name(methodMetadata.getHandlerIdStr())
                .metrics()
                .timeout(timeout)
                .doOnError(t -> handleCallError(t, methodMetadata));
    }

    /**
     * requestChannel请求封装, 用于子类扩展
     */
    protected Flux<Payload> requestChannel(ReactiveMethodMetadata methodMetadata, ByteBuf compositeMetadataBytes,
                                           ByteBuf routeBytes, Flux<Object> paramBodys) {
        //第一个是signal, 作route
        Payload routePayload = ByteBufPayload.create(routeBytes, compositeMetadataBytes);
        Flux<Payload> payloadFlux = paramBodys.startWith(routePayload).map(obj -> {
            if (obj instanceof Payload) {
                return (Payload) obj;
            }
            //param payload 只要带ReactiveMethodMetadata的CompositeMetadataBytes, 而不用带zipkin信息
            return ByteBufPayload.create(
                    ObjectCodecs.INSTANCE.encodeResult(obj, methodMetadata.getDataEncodingType()),
                    methodMetadata.getCompositeMetadataBytes());
        });
        return selector.select(serviceId)
                .requestChannel(payloadFlux)
                .name(methodMetadata.getHandlerIdStr())
                .metrics()
                .timeout(timeout)
                .doOnError(t -> handleCallError(t, methodMetadata));
    }

    /**
     * rsocket请求异常统一处理
     */
    private void handleCallError(Throwable throwable, ReactiveMethodMetadata methodMetadata) {
        if (throwable instanceof TimeoutException) {
            metricsTimeout(methodMetadata);
            log.error(String.format("'%s' call timeout after %s seconds", methodMetadata.getHandlerIdStr(), timeout), throwable);
        }
    }

    /**
     * 从{@link RSocketCompositeMetadata}获取{@link MessageMimeTypeMetadata}元数据
     */
    private RSocketMimeType extractPayloadDataMimeType(RSocketCompositeMetadata compositeMetadata, RSocketMimeType defaultEncodingType) {
        if (compositeMetadata.contains(RSocketMimeType.MESSAGE_MIME_TYPE)) {
            MessageMimeTypeMetadata mimeTypeMetadata = compositeMetadata.getMetadata(RSocketMimeType.MESSAGE_MIME_TYPE);
            return mimeTypeMetadata.getMessageMimeType();
        }
        return defaultEncodingType;
    }

    private void metrics(ReactiveMethodMetadata methodMetadata) {
        Metrics.counter(this.service.concat(MetricsNames.COUNT_SUFFIX), methodMetadata.getMetricsTags()).increment();
    }

    private void metricsTimeout(ReactiveMethodMetadata methodMetadata) {
        Metrics.counter(MetricsNames.RSOCKET_TIMEOUT_ERROR_COUNT, methodMetadata.getMetricsTags()).increment();
    }
}