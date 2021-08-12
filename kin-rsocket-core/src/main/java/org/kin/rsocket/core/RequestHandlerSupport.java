package org.kin.rsocket.core;

import io.netty.util.ReferenceCountUtil;
import io.rsocket.Payload;
import io.rsocket.exceptions.InvalidException;
import io.rsocket.util.ByteBufPayload;
import org.kin.framework.log.LoggerOprs;
import org.kin.framework.utils.ExceptionUtils;
import org.kin.rsocket.core.codec.Codecs;
import org.kin.rsocket.core.metadata.GSVRoutingMetadata;
import org.kin.rsocket.core.metadata.MessageAcceptMimeTypesMetadata;
import org.kin.rsocket.core.metadata.MessageMimeTypeMetadata;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * requester reques
 *
 * @author huangjianqin
 * @date 2021/3/26
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public abstract class RequestHandlerSupport extends AbstractRSocket implements LoggerOprs {
    /** cloud event source */
    protected volatile String cloudEventSource;

    /**
     * 用于log 或者返回异常tips
     *
     * @return 未能找到合适的service method invoker
     */
    private String noServiceMethodInvokerFoundTips(String serviceName, String handleName) {
        return String.format("No service method Invoker found: %s.%s", serviceName, handleName);
    }

    /**
     * 用于log
     *
     * @return service method call exception
     */
    private String failCallLog() {
        return "Failed to call service stub";
    }

    /**
     * 用于返回异常tips
     *
     * @return service method call exception info
     */
    private String failCallTips(Exception e) {
        return "Service invoked failed: " + e.getMessage();
    }

    /**
     * 本地调用服务接口方法并针对RequestResponse Frame Type场景定制额外逻辑
     */
    protected Mono<Payload> localRequestResponse(GSVRoutingMetadata routing,
                                                 MessageMimeTypeMetadata dataEncodingMetadata,
                                                 MessageAcceptMimeTypesMetadata acceptMimeTypesMetadata,
                                                 Payload payload) {
        try {
            ReactiveMethodInvoker methodInvoker = RSocketServiceRegistry.INSTANCE.getInvoker(routing.handlerId());
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
                            error(failCallLog(), e);
                            sink.error(e);
                        }
                    });
                }
                //composite data for return value
                RSocketMimeType resultEncodingType = resultEncodingType(acceptMimeTypesMetadata, dataEncodingMetadata.getMessageMimeType(), methodInvoker);
                Mono<Object> monoResult;
                if (result instanceof Mono) {
                    monoResult = (Mono) result;
                } else {
                    monoResult = ReactiveObjAdapter.INSTANCE.toMono(result);
                }
                return monoResult
                        .map(object -> Codecs.INSTANCE.encodeResult(object, resultEncodingType))
                        .map(dataByteBuf -> ByteBufPayload.create(dataByteBuf, Codecs.INSTANCE.getDefaultCompositeMetadataByteBuf(resultEncodingType)));
            } else {
                ReferenceCountUtil.safeRelease(payload);
                return Mono.error(new InvalidException(noServiceMethodInvokerFoundTips(routing.getService(), routing.getHandler())));
            }
        } catch (Exception e) {
            error(failCallLog(), e);
            ReferenceCountUtil.safeRelease(payload);
            return Mono.error(new InvalidException(failCallTips(e)));
        }
    }

    /**
     * 本地调用服务接口方法并针对FireAndForget Frame Type场景定制额外逻辑
     */
    protected Mono<Void> localFireAndForget(GSVRoutingMetadata routing, MessageMimeTypeMetadata dataEncodingMetadata, Payload payload) {
        ReactiveMethodInvoker methodInvoker = RSocketServiceRegistry.INSTANCE.getInvoker(routing.handlerId());
        if (methodInvoker != null) {
            if (methodInvoker.isAsyncReturn()) {
                try {
                    return ReactiveObjAdapter.INSTANCE.toMono(invokeServiceMethod(methodInvoker, dataEncodingMetadata, payload));
                } catch (Exception e) {
                    ReferenceCountUtil.safeRelease(payload);
                    error(failCallLog(), e);
                    return Mono.error(e);
                }
            } else {
                return Mono.create((sink) -> {
                    try {
                        invokeServiceMethod(methodInvoker, dataEncodingMetadata, payload);
                        sink.success();
                    } catch (Exception e) {
                        error(failCallLog(), e);
                        sink.error(e);
                    }
                });
            }
        } else {
            ReferenceCountUtil.safeRelease(payload);
            return Mono.error(new InvalidException(noServiceMethodInvokerFoundTips(routing.getService(), routing.getHandler())));
        }
    }

    /**
     * 本地调用服务接口方法并针对RequestStream Frame Type场景定制额外逻辑
     */
    protected Flux<Payload> localRequestStream(GSVRoutingMetadata routing,
                                               MessageMimeTypeMetadata dataEncodingMetadata,
                                               MessageAcceptMimeTypesMetadata acceptMimeTypesMetadata,
                                               Payload payload) {
        try {
            ReactiveMethodInvoker methodInvoker = RSocketServiceRegistry.INSTANCE.getInvoker(routing.handlerId());
            if (methodInvoker != null) {
                Object result = invokeServiceMethod(methodInvoker, dataEncodingMetadata, payload);
                Flux<Object> fluxResult;
                if (result instanceof Flux) {
                    fluxResult = (Flux<Object>) result;
                } else {
                    fluxResult = ReactiveObjAdapter.INSTANCE.toFlux(result);
                }
                //composite data for return value
                RSocketMimeType resultEncodingType = resultEncodingType(acceptMimeTypesMetadata, dataEncodingMetadata.getMessageMimeType(), methodInvoker);
                return fluxResult
                        .map(object -> Codecs.INSTANCE.encodeResult(object, resultEncodingType))
                        .map(dataByteBuf -> ByteBufPayload.create(dataByteBuf, Codecs.INSTANCE.getDefaultCompositeMetadataByteBuf(resultEncodingType)));
            } else {
                ReferenceCountUtil.safeRelease(payload);
                return Flux.error(new InvalidException(noServiceMethodInvokerFoundTips(routing.getService(), routing.getHandler())));
            }
        } catch (Exception e) {
            error(failCallLog(), e);
            ReferenceCountUtil.safeRelease(payload);
            return Flux.error(new InvalidException(failCallTips(e)));
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
        try {
            ReactiveMethodInvoker methodInvoker = RSocketServiceRegistry.INSTANCE.getInvoker(routing.handlerId());
            if (methodInvoker != null) {
                Object result;
                if (methodInvoker.getParamCount() == 1) {
                    Flux<Object> paramFlux = payloads
                            .map(payload -> Codecs.INSTANCE.decodeResult(
                                    dataEncodingMetadata.getMessageMimeType(),
                                    payload.data(),
                                    methodInvoker.getInferredClassForParameter(0)));
                    result = methodInvoker.invoke(paramFlux);
                } else {
                    Object paramFirst = Codecs.INSTANCE.decodeResult(
                            dataEncodingMetadata.getMessageMimeType(),
                            signal.data(),
                            methodInvoker.getParameterTypes()[0]);
                    Flux<Object> paramFlux = payloads
                            .map(payload -> Codecs.INSTANCE.decodeResult(
                                    dataEncodingMetadata.getMessageMimeType(),
                                    payload.data(),
                                    methodInvoker.getInferredClassForParameter(1)));
                    result = methodInvoker.invoke(paramFirst, paramFlux);
                }
                if (result instanceof Mono) {
                    result = Flux.from((Mono<?>) result);
                } else {
                    result = ReactiveObjAdapter.INSTANCE.toFlux(result);
                }
                //composite data for return value
                RSocketMimeType resultEncodingType = resultEncodingType(acceptMimeTypesMetadata, dataEncodingMetadata.getMessageMimeType(), methodInvoker);
                //result return
                return ((Flux<?>) result)
                        .map(object -> Codecs.INSTANCE.encodeResult(object, resultEncodingType))
                        .map(dataByteBuf -> ByteBufPayload.create(dataByteBuf, Codecs.INSTANCE.getDefaultCompositeMetadataByteBuf(resultEncodingType)));
            } else {
                return Flux.error(new InvalidException(noServiceMethodInvokerFoundTips(routing.getService(), routing.getHandler())));
            }
        } catch (Exception e) {
            error(failCallLog(), e);
            //release
            ReferenceCountUtil.safeRelease(signal);
            payloads.subscribe(ReferenceCountUtil::safeRelease);
            return Flux.error(new InvalidException(failCallTips(e)));
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
                Object args = Codecs.INSTANCE.decodeParams(dataEncodingMetadata.getMessageMimeType(), payload.data(), methodInvoker.getParameterTypes());
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

