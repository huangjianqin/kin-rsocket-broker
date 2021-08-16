package org.kin.rsocket.service;

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
import org.kin.framework.utils.ExceptionUtils;
import org.kin.framework.utils.MethodHandleUtils;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.core.*;
import org.kin.rsocket.core.codec.Codecs;
import org.kin.rsocket.core.metadata.MessageMimeTypeMetadata;
import org.kin.rsocket.core.metadata.RSocketCompositeMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.InvocationHandler;
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
public final class RequesterProxy implements InvocationHandler {
    private static final Logger log = LoggerFactory.getLogger(RequesterProxy.class);
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
            service = serviceInterface.getCanonicalName();
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

        RSocketMimeType[] acceptEncodingTypes = builder.getAcceptEncodingTypes();
        if (acceptEncodingTypes == null) {
            this.defaultAcceptEncodingTypes = defaultAcceptEncodingTypes();
        } else {
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

        ReactiveMethodMetadata methodMetadata = methodMetadataMap.get(method);
        if (Objects.isNull(methodMetadata)) {
            //lazy init method metadata
            methodMetadata = new ReactiveMethodMetadata(group, service, version,
                    method, defaultEncodingType, defaultAcceptEncodingTypes, endpoint, sticky, sourceUri);
            methodMetadataMap.put(method, methodMetadata);
        }
        MutableContext mutableContext = new MutableContext();
        mutableContext.put(ReactiveMethodMetadata.class, methodMetadata);

        if (methodMetadata.getRsocketFrameType() == FrameType.REQUEST_CHANNEL) {
            //request channel
            Payload routePayload;
            Flux<Object> source;
            if (args.length == 1) {
                //1 param
                routePayload = ByteBufPayload.create(Unpooled.EMPTY_BUFFER, methodMetadata.getCompositeMetadataBytes());
                source = ReactiveObjAdapter.INSTANCE.toFlux(args[0]);
            } else {
                //2 params
                ByteBuf bodyBuffer = Codecs.INSTANCE.encodeResult(args[0], methodMetadata.getDataEncodingType());
                routePayload = ByteBufPayload.create(bodyBuffer, methodMetadata.getCompositeMetadataBytes());
                source = ReactiveObjAdapter.INSTANCE.toFlux(args[1]);
            }
            //第一个signal
            ReactiveMethodMetadata finalMethodMetadata = methodMetadata;
            Flux<Payload> payloadFlux = source.startWith(routePayload).map(obj -> {
                if (obj instanceof Payload) {
                    return (Payload) obj;
                }
                return ByteBufPayload.create(
                        Codecs.INSTANCE.encodeResult(obj, finalMethodMetadata.getDataEncodingType()),
                        finalMethodMetadata.getCompositeMetadataBytes());
            });
            Flux<Payload> payloads = selector.select(serviceId).requestChannel(payloadFlux);
            //handle return
            Flux<Object> fluxReturn = payloads.concatMap(payload -> {
                try {
                    RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(payload.metadata());
                    return Mono.justOrEmpty(Codecs.INSTANCE.decodeResult(
                            extractPayloadDataMimeType(compositeMetadata, finalMethodMetadata.getAcceptEncodingTypes()[0]),
                            payload.data(),
                            finalMethodMetadata.getInferredClassForReturn()));
                } catch (Exception e) {
                    return Flux.error(e);
                }
            }).contextWrite(c -> mutableContext.putAll(c.readOnly()));
            if (methodMetadata.isMonoChannel()) {
                return fluxReturn.last();
            } else {
                return fluxReturn;
            }
        } else {
            //body content
            ByteBuf paramBodyBytes = Codecs.INSTANCE.encodeParams(args, methodMetadata.getDataEncodingType());
            if (methodMetadata.getRsocketFrameType() == FrameType.REQUEST_RESPONSE) {
                //request response
                ReactiveMethodMetadata finalMethodMetadata = methodMetadata;
                Mono<Payload> payloadMono = selector.select(serviceId).requestResponse(ByteBufPayload.create(paramBodyBytes, methodMetadata.getCompositeMetadataBytes()))
                        .name(methodMetadata.getFullName())
                        .metrics()
                        .timeout(timeout)
                        .doOnError(TimeoutException.class,
                                e -> log.error(String.format("Timeout to call %s in %s seconds", finalMethodMetadata.getFullName(), timeout), e));

                Mono<Object> result = payloadMono.handle((payload, sink) -> {
                    try {
                        RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(payload.metadata());
                        Object obj = Codecs.INSTANCE.decodeResult(
                                extractPayloadDataMimeType(compositeMetadata, finalMethodMetadata.getAcceptEncodingTypes()[0]),
                                payload.data(),
                                finalMethodMetadata.getInferredClassForReturn());
                        if (obj != null) {
                            sink.next(obj);
                        }
                        sink.complete();
                    } catch (Exception e) {
                        sink.error(e);
                    }
                });
                return ReactiveObjAdapter.INSTANCE.fromPublisher(result, mutableContext);
            } else if (methodMetadata.getRsocketFrameType() == FrameType.REQUEST_FNF) {
                //request and forget
                return selector.select(serviceId).fireAndForget(ByteBufPayload.create(paramBodyBytes, methodMetadata.getCompositeMetadataBytes()));
            } else if (methodMetadata.getRsocketFrameType() == FrameType.REQUEST_STREAM) {
                //request stream
                ReactiveMethodMetadata finalMethodMetadata = methodMetadata;
                Flux<Payload> flux = selector.select(serviceId).requestStream(ByteBufPayload.create(paramBodyBytes, methodMetadata.getCompositeMetadataBytes()));
                Flux<Object> result = flux.concatMap((payload) -> {
                    try {
                        RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(payload.metadata());
                        return Mono.justOrEmpty(Codecs.INSTANCE.decodeResult(
                                extractPayloadDataMimeType(compositeMetadata, finalMethodMetadata.getAcceptEncodingTypes()[0]),
                                payload.data(),
                                finalMethodMetadata.getInferredClassForReturn()));
                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                });
                return ReactiveObjAdapter.INSTANCE.fromPublisher(result, mutableContext);
            } else {
                ReferenceCountUtil.safeRelease(paramBodyBytes);
                return Mono.error(new Exception("Unknown RSocket Frame type: " + methodMetadata.getRsocketFrameType().name()));
            }
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
}