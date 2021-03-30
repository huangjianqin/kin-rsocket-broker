package org.kin.rsocket.broker;

import com.fasterxml.jackson.databind.JsonNode;
import io.netty.util.ReferenceCountUtil;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.exceptions.ApplicationErrorException;
import io.rsocket.exceptions.InvalidException;
import io.rsocket.frame.FrameType;
import org.kin.rsocket.core.AbstractRSocket;
import org.kin.rsocket.core.RSocketFilterChain;
import org.kin.rsocket.core.RSocketFilterContext;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.broker.UpstreamClusterChangedEvent;
import org.kin.rsocket.core.metadata.AppMetadata;
import org.kin.rsocket.core.metadata.GSVRoutingMetadata;
import org.kin.rsocket.core.metadata.RSocketCompositeMetadata;
import org.kin.rsocket.core.metadata.RSocketMimeType;
import org.kin.rsocket.core.utils.JSON;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.extra.processor.TopicProcessor;

/**
 * broker间rsocket connection建立创建的Responder
 * 代码与{@link ServiceResponder}有点类似, 看看能不能抽象一下
 *
 * @author huangjianqin
 * @date 2021/3/30
 */
public class BrokerResponder extends AbstractRSocket {
    private static final Logger log = LoggerFactory.getLogger(BrokerResponder.class);
    private final ServiceRouteTable routeTable;
    private final ServiceRouter serviceRouter;
    private final RSocketFilterChain filterChain;
    /** 本broker app metadata todo 为何不从外部getAppMeta获取 */
    private final AppMetadata upstreamBrokerMetadata;
    /** reactive event processor */
    private final TopicProcessor<CloudEventData<?>> eventProcessor;

    public BrokerResponder(ServiceRouteTable serviceRoutingSelector,
                           ServiceRouter serviceRouter,
                           RSocketFilterChain filterChain,
                           TopicProcessor<CloudEventData<?>> eventProcessor) {
        this.routeTable = serviceRoutingSelector;
        this.filterChain = filterChain;
        this.serviceRouter = serviceRouter;
        this.eventProcessor = eventProcessor;

        this.upstreamBrokerMetadata = new AppMetadata();
        //todo 看看CentralBroker是否需要修改
        this.upstreamBrokerMetadata.setName("CentralBroker");
    }


    @Override
    public Mono<Void> fireAndForget(Payload payload) {
        RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(payload.metadata());
        GSVRoutingMetadata gsvRoutingMetadata = compositeMetadata.getMetadata(RSocketMimeType.Routing);
        if (gsvRoutingMetadata == null) {
            return Mono.error(new InvalidException("No Routing metadata"));
        }
        //request filters
        Mono<RSocket> destination = findDestination(gsvRoutingMetadata);
        if (this.filterChain.isFiltersPresent()) {
            RSocketFilterContext filterContext = RSocketFilterContext.of(FrameType.REQUEST_FNF, gsvRoutingMetadata, this.upstreamBrokerMetadata, payload);
            destination = filterChain.filter(filterContext).then(destination);
        }
        //call destination
        return destination.flatMap(rsocket -> rsocket.fireAndForget(payload));
    }

    @Override
    public Mono<Payload> requestResponse(Payload payload) {
        RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(payload.metadata());
        GSVRoutingMetadata gsvRoutingMetadata = compositeMetadata.getMetadata(RSocketMimeType.Routing);
        if (gsvRoutingMetadata == null) {
            return Mono.error(new InvalidException("No Routing metadata"));
        }
        //request filters
        Mono<RSocket> destination = findDestination(gsvRoutingMetadata);
        if (this.filterChain.isFiltersPresent()) {
            RSocketFilterContext filterContext = RSocketFilterContext.of(FrameType.REQUEST_RESPONSE, gsvRoutingMetadata, this.upstreamBrokerMetadata, payload);
            destination = filterChain.filter(filterContext).then(destination);
        }
        //call destination
        return destination.flatMap(rsocket -> rsocket.requestResponse(payload));
    }

    @Override
    public Flux<Payload> requestStream(Payload payload) {
        RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(payload.metadata());
        GSVRoutingMetadata gsvRoutingMetadata = compositeMetadata.getMetadata(RSocketMimeType.Routing);
        if (gsvRoutingMetadata == null) {
            return Flux.error(new InvalidException("No Routing metadata"));
        }
        Mono<RSocket> destination = findDestination(gsvRoutingMetadata);
        if (this.filterChain.isFiltersPresent()) {
            RSocketFilterContext filterContext = RSocketFilterContext.of(FrameType.REQUEST_STREAM, gsvRoutingMetadata, this.upstreamBrokerMetadata, payload);
            destination = filterChain.filter(filterContext).then(destination);
        }
        return destination.flatMapMany(rsocket -> rsocket.requestStream(payload));
    }

    @Override
    public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
        Flux<Payload> payloadsWithSignalRouting = (Flux<Payload>) payloads;
        //noinspection ConstantConditions
        return payloadsWithSignalRouting.switchOnFirst((signal, flux) -> requestChannel(signal.get(), flux));
    }

    private Flux<Payload> requestChannel(Payload signal, Publisher<Payload> payloads) {
        RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(signal.metadata());
        GSVRoutingMetadata gsvRoutingMetadata = compositeMetadata.getMetadata(RSocketMimeType.Routing);
        if (gsvRoutingMetadata == null) {
            return Flux.error(new InvalidException("No Routing metadata"));
        }
        Mono<RSocket> destination = findDestination(gsvRoutingMetadata);
        return destination.flatMapMany(rsocket -> rsocket.requestChannel(payloads));
    }

    @Override
    public Mono<Void> metadataPush(Payload payload) {
        try {
            if (payload.metadata().readableBytes() > 0) {
                //todo 看看编码是否对得上
                CloudEventData<JsonNode> cloudEvent = JSON.decodeValue(payload.getMetadataUtf8());
                String type = cloudEvent.getAttributes().getType();
                if (UpstreamClusterChangedEvent.class.getCanonicalName().equalsIgnoreCase(type)) {
                    //TODO 目前仅仅处理一种cloudevent, 还是说全开放, 会不会有其他异常
                    eventProcessor.onNext(cloudEvent);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse Cloud Event: ".concat(e.getMessage()), e);
        } finally {
            ReferenceCountUtil.safeRelease(payload);
        }
        return Mono.empty();
    }

    /**
     * 寻找目标服务provider instance
     */
    private Mono<RSocket> findDestination(GSVRoutingMetadata routingMetaData) {
        return Mono.create(sink -> {
            String gsv = routingMetaData.gsv();
            Integer serviceId = routingMetaData.id();
            ServiceResponder targetResponder = null;
            RSocket rsocket = null;
            Exception error = null;
            String endpoint = routingMetaData.getEndpoint();
            if (endpoint != null && !endpoint.isEmpty()) {
                targetResponder = findDestinationWithEndpoint(endpoint, serviceId);
                if (targetResponder == null) {
                    error = new InvalidException(String.format("Service not found with endpoint '%s' '%s'", gsv, endpoint));
                }
            } else {
                Integer targetInstanceId = routeTable.getInstanceId(serviceId);
                if (targetInstanceId != null) {
                    targetResponder = serviceRouter.getByInstanceId(targetInstanceId);
                } else {
                    error = new InvalidException(String.format("Service not found '%s'", gsv));
                }
            }
            //security check
            if (targetResponder != null) {
                rsocket = targetResponder.getPeerRsocket();
            }
            if (rsocket != null) {
                sink.success(rsocket);
            } else if (error != null) {
                sink.error(error);
            } else {
                sink.error(new ApplicationErrorException(String.format("Service not found '%s'", gsv)));
            }
        });
    }

    /**
     * todo
     */
    private ServiceResponder findDestinationWithEndpoint(String endpoint, Integer serviceId) {
        if (endpoint.startsWith("id:")) {
            return serviceRouter.getByUUID(endpoint.substring(3));
        }
        int endpointHashCode = endpoint.hashCode();
        for (Integer instanceId : routeTable.getAllInstanceIds(serviceId)) {
            ServiceResponder responder = serviceRouter.getByInstanceId(instanceId);
            if (responder != null) {
                if (responder.getAppTagsHashCodeSet().contains(endpointHashCode)) {
                    return responder;
                }
            }
        }
        return null;
    }
}