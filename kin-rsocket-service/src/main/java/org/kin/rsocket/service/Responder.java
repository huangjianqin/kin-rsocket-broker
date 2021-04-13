package org.kin.rsocket.service;

import io.netty.util.ReferenceCountUtil;
import io.rsocket.ConnectionSetupPayload;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.exceptions.InvalidException;
import org.kin.rsocket.core.RSocketAppContext;
import org.kin.rsocket.core.ReactiveServiceRegistry;
import org.kin.rsocket.core.ResponderRsocket;
import org.kin.rsocket.core.ResponderSupport;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.CloudEventRSocket;
import org.kin.rsocket.core.event.CloudEventReply;
import org.kin.rsocket.core.metadata.*;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.extra.processor.TopicProcessor;

import java.net.URI;

/**
 * 每个连接的responder
 *
 * @author huangjianqin
 * @date 2021/3/28
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class Responder extends ResponderSupport implements CloudEventRSocket, ResponderRsocket {
    /** requester from peer */
    protected RSocket requester;
    /**
     * 默认数据编码类型, 从requester setup payload解析而来
     * 返回数据给requester时, 如果没有带数据编码类型, 则使用默认的编码类型进行编码
     */
    protected MessageMimeTypeMetadata defaultMessageMimeTypeMetadata = null;
    protected TopicProcessor<CloudEventData<?>> eventProcessor;
    /** combo onClose from responder and requester */
    private Mono<Void> comboOnClose;

    public Responder(ReactiveServiceRegistry serviceRegistry,
                     TopicProcessor<CloudEventData<?>> eventProcessor,
                     RSocket requester,
                     ConnectionSetupPayload setupPayload) {
        super(serviceRegistry);
        this.eventProcessor = eventProcessor;
        this.requester = requester;
        this.comboOnClose = Mono.first(super.onClose(), requester.onClose());

        //解析composite metadata
        RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(setupPayload.metadata());
        if (compositeMetadata.contains(RSocketMimeType.Application)) {
            AppMetadata appMetadata = compositeMetadata.getMetadata(RSocketMimeType.Application);
            //from remote requester
            if (!appMetadata.getUuid().equals(RSocketAppContext.ID)) {
                RSocketMimeType dataType = RSocketMimeType.getByType(setupPayload.dataMimeType());
                if (dataType != null) {
                    this.defaultMessageMimeTypeMetadata = MessageMimeTypeMetadata.of(dataType);
                }
            }
        }
    }

    /**
     * No Routing metadata异常的{@link Mono}实例
     */
    private Mono noRoutingDataErrorMono() {
        return Mono.error(new InvalidException("No Routing metadata"));
    }

    /**
     * No encoding metadata异常的{@link Mono}实例
     */
    private Mono noEncodingDataErrorMono() {
        return Mono.error(new InvalidException("No encoding metadata"));
    }

    /**
     * No Routing metadata异常的{@link Flux}实例
     */
    private Flux noRoutingDataErrorFlux() {
        return Flux.error(new InvalidException("No Routing metadata"));
    }

    /**
     * No encoding metadata异常的{@link Flux}实例
     */
    private Flux noEncodingDataErrorFlux() {
        return Flux.error(new InvalidException("No encoding metadata"));
    }

    @Override
    public Mono<Payload> requestResponse(Payload payload) {
        RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(payload.metadata());
        GSVRoutingMetadata routingMetaData = getGsvRoutingMetadata(compositeMetadata);
        if (routingMetaData == null) {
            ReferenceCountUtil.safeRelease(payload);
            return noRoutingDataErrorMono();
        }
        MessageMimeTypeMetadata dataEncodingMetadata = getDataEncodingMetadata(compositeMetadata);
        if (dataEncodingMetadata == null) {
            ReferenceCountUtil.safeRelease(payload);
            return noEncodingDataErrorMono();
        }
        return localRequestResponse(routingMetaData, dataEncodingMetadata, compositeMetadata.getMetadata(RSocketMimeType.MessageAcceptMimeTypes), payload);
    }

    @Override
    public Mono<Void> fireAndForget(Payload payload) {
        RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(payload.metadata());
        GSVRoutingMetadata routingMetaData = getGsvRoutingMetadata(compositeMetadata);
        if (routingMetaData == null) {
            ReferenceCountUtil.safeRelease(payload);
            return noRoutingDataErrorMono();
        }
        MessageMimeTypeMetadata dataEncodingMetadata = getDataEncodingMetadata(compositeMetadata);
        if (dataEncodingMetadata == null) {
            ReferenceCountUtil.safeRelease(payload);
            return noEncodingDataErrorMono();
        }
        return localFireAndForget(routingMetaData, dataEncodingMetadata, payload);
    }

    @Override
    public Mono<Void> fireCloudEvent(CloudEventData<?> cloudEvent) {
        return Mono.fromRunnable(() -> eventProcessor.onNext(cloudEvent));
    }

    @Override
    public Mono<Void> fireCloudEventReply(URI replayTo, CloudEventReply eventReply) {
        return requester.fireAndForget(cloudEventReply2Payload(replayTo, eventReply));
    }

    @Override
    public Flux<Payload> requestStream(Payload payload) {
        RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(payload.metadata());
        GSVRoutingMetadata routingMetaData = getGsvRoutingMetadata(compositeMetadata);
        if (routingMetaData == null) {
            ReferenceCountUtil.safeRelease(payload);
            return noRoutingDataErrorFlux();
        }
        MessageMimeTypeMetadata dataEncodingMetadata = getDataEncodingMetadata(compositeMetadata);
        if (dataEncodingMetadata == null) {
            ReferenceCountUtil.safeRelease(payload);
            return noEncodingDataErrorFlux();
        }
        return localRequestStream(routingMetaData, dataEncodingMetadata, compositeMetadata.getMetadata(RSocketMimeType.MessageAcceptMimeTypes), payload);
    }

    private Flux<Payload> requestChannel(Payload signal, Publisher<Payload> payloads) {
        RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(signal.metadata());
        GSVRoutingMetadata routingMetaData = getGsvRoutingMetadata(compositeMetadata);
        if (routingMetaData == null) {
            ReferenceCountUtil.safeRelease(signal);
            return noRoutingDataErrorFlux();
        }
        MessageMimeTypeMetadata dataEncodingMetadata = getDataEncodingMetadata(compositeMetadata);
        if (dataEncodingMetadata == null) {
            ReferenceCountUtil.safeRelease(signal);
            return noEncodingDataErrorFlux();
        }

        return localRequestChannel(routingMetaData, dataEncodingMetadata,
                compositeMetadata.getMetadata(RSocketMimeType.MessageAcceptMimeTypes), signal,
                ((Flux<Payload>) payloads).skip(1));
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public final Flux<Payload> requestChannel(Publisher<Payload> payloads) {
        Flux<Payload> payloadsWithSignalRouting = (Flux<Payload>) payloads;
        return payloadsWithSignalRouting.switchOnFirst((signal, flux) -> requestChannel(signal.get(), flux));
    }

    @Override
    public Mono<Void> metadataPush(Payload payload) {
        try {
            if (payload.metadata().readableBytes() > 0) {
                CloudEventData<?> cloudEvent = extractCloudEventsFromMetadata(payload);
                if (cloudEvent != null) {
                    return fireCloudEvent(cloudEvent);
                }
            }
        } catch (Exception e) {
            error("Failed to parse Cloud Event:  " + e.getMessage(), e);
        } finally {
            ReferenceCountUtil.safeRelease(payload);
        }
        return Mono.empty();
    }

    @Override
    public Mono<Void> fireCloudEventToPeer(CloudEventData<?> cloudEvent) {
        try {
            Payload payload = cloudEvent2Payload(cloudEvent);
            return requester.metadataPush(payload);
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    @Override
    public Mono<Void> onClose() {
        return this.comboOnClose;
    }

    /**
     * 解析并获取{@link MessageMimeTypeMetadata}
     */
    private MessageMimeTypeMetadata getDataEncodingMetadata(RSocketCompositeMetadata compositeMetadata) {
        MessageMimeTypeMetadata dataEncodingMetadata = compositeMetadata.getMetadata(RSocketMimeType.MessageMimeType);
        if (dataEncodingMetadata == null) {
            return defaultMessageMimeTypeMetadata;
        } else {
            return dataEncodingMetadata;
        }
    }

    /**
     * 解析并获取{@link GSVRoutingMetadata}
     */
    private GSVRoutingMetadata getGsvRoutingMetadata(RSocketCompositeMetadata compositeMetadata) {
        return compositeMetadata.getMetadata(RSocketMimeType.Routing);
    }
}
