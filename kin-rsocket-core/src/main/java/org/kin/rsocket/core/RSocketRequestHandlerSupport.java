package org.kin.rsocket.core;

import io.netty.util.ReferenceCountUtil;
import io.rsocket.Payload;
import io.rsocket.exceptions.InvalidException;
import io.rsocket.util.ByteBufPayload;
import org.kin.framework.utils.ExceptionUtils;
import org.kin.rsocket.core.codec.ObjectCodecs;
import org.kin.rsocket.core.metadata.GSVRoutingMetadata;
import org.kin.rsocket.core.metadata.MessageAcceptMimeTypesMetadata;
import org.kin.rsocket.core.metadata.MessageMimeTypeMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * requester request
 *
 * @author huangjianqin
 * @date 2021/3/26
 */
@SuppressWarnings({"unchecked"})
public abstract class RSocketRequestHandlerSupport extends AbstractRSocket {
    private static final Logger log = LoggerFactory.getLogger(RSocketRequestHandlerSupport.class);
    /** cloud event source */
    protected volatile String cloudEventSource;

    /**
     * 用于log 或者返回异常tips
     *
     * @return 未能找到合适的service method invoker
     */
    private String noServiceMethodInvokerFoundTips(String service, String handler) {
        return String.format("No service method Invoker found: %s.%s", service, handler);
    }

    /**
     * 用于log
     *
     * @return service method call exception
     */
    private String failCallLog(String service, String handler) {
        return String.format("Failed to call service stub %s#%s", service, handler);
    }

    /**
     * 用于返回异常tips
     *
     * @return service method call exception info
     */
    private String failCallTips(String service, String handle, Exception e) {
        return String.format("Service '%s' invoked '%s' failed: %s", service, handle, e.getMessage());
    }

    /**
     * 本地调用服务接口方法并针对RequestResponse Frame Type场景定制额外逻辑
     */
    protected Mono<Payload> localRequestResponse(GSVRoutingMetadata routing,
                                                 MessageMimeTypeMetadata dataEncodingMetadata,
                                                 MessageAcceptMimeTypesMetadata acceptMimeTypesMetadata,
                                                 Payload payload) {
        String service = routing.getService();
        String handler = routing.getHandler();
        try {
            ReactiveMethodInvoker methodInvoker = LocalRSocketServiceRegistry.INSTANCE.getInvoker(routing.handlerId());
            if (methodInvoker != null) {
                Object result;
                if (methodInvoker.isAsyncReturn()) {
                    result = invokeServiceMethod(methodInvoker, dataEncodingMetadata, payload);
                } else {
                    result = Mono.create((sink) -> {
                        try {
                            Object resultObj = invokeServiceMethod(methodInvoker, dataEncodingMetadata, payload);

                            if (resultObj == null) {
                                sink.success();
                            } else if (resultObj instanceof Mono) {
                                Mono<Object> monoObj = (Mono<Object>) resultObj;
                                monoObj.doOnError(sink::error)
                                        .doOnNext(sink::success)
                                        .thenEmpty(Mono.fromRunnable(sink::success))
                                        .subscribe();
                            } else {
                                sink.success(resultObj);
                            }
                        } catch (Exception e) {
                            log.error(failCallLog(service, handler), e);
                            sink.error(e);
                        }
                    });
                }
                //composite data for return value
                RSocketMimeType resultEncodingType = resultEncodingType(acceptMimeTypesMetadata, dataEncodingMetadata.getMessageMimeType(), methodInvoker);
                return ReactiveObjAdapter.INSTANCE.toMono(result)
                        .map(object -> ObjectCodecs.INSTANCE.encodeResult(object, resultEncodingType))
                        .map(dataByteBuf -> ByteBufPayload.create(dataByteBuf, ObjectCodecs.INSTANCE.getDefaultCompositeMetadataByteBuf(resultEncodingType)));
            } else {
                ReferenceCountUtil.safeRelease(payload);
                return Mono.error(new InvalidException(noServiceMethodInvokerFoundTips(service, handler)));
            }
        } catch (Exception e) {
            log.error(failCallLog(service, handler), e);
            return Mono.error(new InvalidException(failCallTips(service, handler, e)));
        }
    }

    /**
     * 本地调用服务接口方法并针对FireAndForget Frame Type场景定制额外逻辑
     */
    protected Mono<Void> localFireAndForget(GSVRoutingMetadata routing, MessageMimeTypeMetadata dataEncodingMetadata, Payload payload) {
        String service = routing.getService();
        String handler = routing.getHandler();

        ReactiveMethodInvoker methodInvoker = LocalRSocketServiceRegistry.INSTANCE.getInvoker(routing.handlerId());
        if (methodInvoker != null) {
            if (methodInvoker.isAsyncReturn()) {
                try {
                    return ReactiveObjAdapter.INSTANCE.toMono(invokeServiceMethod(methodInvoker, dataEncodingMetadata, payload));
                } catch (Exception e) {
                    log.error(failCallLog(service, handler), e);
                    return Mono.error(e);
                }
            } else {
                return Mono.create((sink) -> {
                    try {
                        invokeServiceMethod(methodInvoker, dataEncodingMetadata, payload);
                        sink.success();
                    } catch (Exception e) {
                        log.error(failCallLog(service, handler), e);
                        sink.error(e);
                    }
                });
            }
        } else {
            ReferenceCountUtil.safeRelease(payload);
            return Mono.error(new InvalidException(noServiceMethodInvokerFoundTips(service, handler)));
        }
    }

    /**
     * 本地调用服务接口方法并针对RequestStream Frame Type场景定制额外逻辑
     */
    protected Flux<Payload> localRequestStream(GSVRoutingMetadata routing,
                                               MessageMimeTypeMetadata dataEncodingMetadata,
                                               MessageAcceptMimeTypesMetadata acceptMimeTypesMetadata,
                                               Payload payload) {
        String service = routing.getService();
        String handler = routing.getHandler();
        try {
            ReactiveMethodInvoker methodInvoker = LocalRSocketServiceRegistry.INSTANCE.getInvoker(routing.handlerId());
            if (methodInvoker != null) {
                Object result = invokeServiceMethod(methodInvoker, dataEncodingMetadata, payload);
                //composite data for return value
                RSocketMimeType resultEncodingType = resultEncodingType(acceptMimeTypesMetadata, dataEncodingMetadata.getMessageMimeType(), methodInvoker);
                return ReactiveObjAdapter.INSTANCE.toFlux(result)
                        .map(object -> ObjectCodecs.INSTANCE.encodeResult(object, resultEncodingType))
                        .map(dataByteBuf -> ByteBufPayload.create(dataByteBuf, ObjectCodecs.INSTANCE.getDefaultCompositeMetadataByteBuf(resultEncodingType)));
            } else {
                ReferenceCountUtil.safeRelease(payload);
                return Flux.error(new InvalidException(noServiceMethodInvokerFoundTips(service, handler)));
            }
        } catch (Exception e) {
            log.error(failCallLog(service, handler), e);
            return Flux.error(new InvalidException(failCallTips(service, handler, e)));
        }
    }

    /**
     * 本地调用服务接口方法并针对RequestChannel Frame Type场景定制额外逻辑
     */
    @SuppressWarnings("ReactiveStreamsNullableInLambdaInTransform")
    protected Flux<Payload> localRequestChannel(GSVRoutingMetadata routing,
                                                MessageMimeTypeMetadata dataEncodingMetadata,
                                                MessageAcceptMimeTypesMetadata acceptMimeTypesMetadata,
                                                Payload signal, Flux<Payload> payloads) {
        String service = routing.getService();
        String handler = routing.getHandler();
        try {
            ReactiveMethodInvoker methodInvoker = LocalRSocketServiceRegistry.INSTANCE.getInvoker(routing.handlerId());
            if (methodInvoker != null) {
                Object result;
                if (methodInvoker.getParamCount() == 1) {
                    Flux<Object> paramFlux = payloads
                            .map(payload -> {
                                try {
                                    return ObjectCodecs.INSTANCE.decodeResult(
                                            dataEncodingMetadata.getMessageMimeType(),
                                            payload.data(),
                                            methodInvoker.getInferredClassForParameter(0));
                                } finally {
                                    ReferenceCountUtil.safeRelease(payload);
                                }
                            });
                    result = methodInvoker.invoke(paramFlux);
                } else {
                    Object paramFirst = ObjectCodecs.INSTANCE.decodeResult(
                            dataEncodingMetadata.getMessageMimeType(),
                            signal.data(),
                            methodInvoker.getParameterTypes()[0]);
                    Flux<Object> paramFlux = payloads
                            .map(payload -> {
                                try {
                                    return ObjectCodecs.INSTANCE.decodeResult(
                                            dataEncodingMetadata.getMessageMimeType(),
                                            payload.data(),
                                            methodInvoker.getInferredClassForParameter(1));
                                } finally {
                                    ReferenceCountUtil.safeRelease(payload);
                                }
                            });
                    result = methodInvoker.invoke(paramFirst, paramFlux);
                    ReferenceCountUtil.safeRelease(signal);
                }
                //composite data for return value
                RSocketMimeType resultEncodingType = resultEncodingType(acceptMimeTypesMetadata, dataEncodingMetadata.getMessageMimeType(), methodInvoker);
                //result return
                return ReactiveObjAdapter.INSTANCE.toFlux(result)
                        .map(object -> ObjectCodecs.INSTANCE.encodeResult(object, resultEncodingType))
                        .map(dataByteBuf -> ByteBufPayload.create(dataByteBuf, ObjectCodecs.INSTANCE.getDefaultCompositeMetadataByteBuf(resultEncodingType)));
            } else {
                //release
                ReferenceCountUtil.safeRelease(signal);
                payloads.subscribe(ReferenceCountUtil::safeRelease);
                return Flux.error(new InvalidException(noServiceMethodInvokerFoundTips(service, handler)));
            }
        } catch (Exception e) {
            log.error(failCallLog(service, handler), e);
            //release
            ReferenceCountUtil.safeRelease(signal);
            payloads.subscribe(ReferenceCountUtil::safeRelease);
            return Flux.error(new InvalidException(failCallTips(service, handler, e)));
        }
    }


    /**
     * invoke service method
     * 如果遇到异常, 则抛出
     */
    private Object invokeServiceMethod(ReactiveMethodInvoker methodInvoker, MessageMimeTypeMetadata dataEncodingMetadata, Payload payload) {
        Object result = null;
        try {
            if (methodInvoker.getParamCount() > 0) {
                Object args = ObjectCodecs.INSTANCE.decodeParams(dataEncodingMetadata.getMessageMimeType(), payload.data(), methodInvoker.getParameterTypes());
                if (args instanceof Object[]) {
                    result = methodInvoker.invoke((Object[]) args);
                } else {
                    result = methodInvoker.invoke(args);
                }
            } else {
                result = methodInvoker.invoke();
            }
        } catch (Exception e) {
            ExceptionUtils.throwExt(e);
        } finally {
            ReferenceCountUtil.safeRelease(payload);
        }
        return result;
    }

    /**
     * @return 接口方法返回结果编码类型
     */
    private RSocketMimeType resultEncodingType(MessageAcceptMimeTypesMetadata acceptMimeTypesMetadata,
                                               RSocketMimeType defaultEncodingType,
                                               ReactiveMethodInvoker methodInvoker) {
        if (methodInvoker.isBinaryReturn()) {
            return RSocketMimeType.BINARY;
        }
        if (acceptMimeTypesMetadata != null) {
            RSocketMimeType firstAcceptType = acceptMimeTypesMetadata.getFirstAcceptType();
            if (firstAcceptType != null) {
                return firstAcceptType;
            }
        }
        return defaultEncodingType;
    }

    //setter
    public void setCloudEventSource(String cloudEventSource) {
        this.cloudEventSource = cloudEventSource;
    }
}

