package org.kin.rsocket.broker;

import com.fasterxml.jackson.databind.JsonNode;
import io.netty.util.ReferenceCountUtil;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.exceptions.ApplicationErrorException;
import io.rsocket.exceptions.InvalidException;
import io.rsocket.frame.FrameType;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.core.AbstractRSocket;
import org.kin.rsocket.core.RSocketAppContext;
import org.kin.rsocket.core.RSocketMimeType;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.UpstreamClusterChangedEvent;
import org.kin.rsocket.core.metadata.AppMetadata;
import org.kin.rsocket.core.metadata.GSVRoutingMetadata;
import org.kin.rsocket.core.metadata.RSocketCompositeMetadata;
import org.kin.rsocket.core.utils.JSON;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * broker <- upstream broker
 * broker responder, , 处理upstream broker request
 *
 * @author huangjianqin
 * @date 2021/3/30
 */
final class DownStreamBrokerResponder extends AbstractRSocket {
    private static final Logger log = LoggerFactory.getLogger(DownStreamBrokerResponder.class);
    private final ServiceManager serviceManager;
    private final RSocketFilterChain filterChain;
    /** broker reuqester app metadata */
    private final AppMetadata upstreamBrokerMetadata;

    public DownStreamBrokerResponder(ServiceManager serviceManager,
                                     RSocketFilterChain filterChain,
                                     Payload setupPayload) {
        this.filterChain = filterChain;
        this.serviceManager = serviceManager;

        //解析composite metadata
        RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(setupPayload.metadata());
        if (compositeMetadata.contains(RSocketMimeType.Application)) {
            upstreamBrokerMetadata = compositeMetadata.getMetadata(RSocketMimeType.Application);
        } else {
            upstreamBrokerMetadata = new AppMetadata();
            upstreamBrokerMetadata.setName("unknown peer broker");
        }
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
                CloudEventData<JsonNode> cloudEvent = JSON.decodeValue(payload.getMetadataUtf8());
                String type = cloudEvent.getAttributes().getType();
                if (UpstreamClusterChangedEvent.class.getCanonicalName().equalsIgnoreCase(type)) {
                    //因为upstream broker是其他rsocket broker集群, 为了保证隔离, 不需要处理其他类型的事件广播
                    RSocketAppContext.CLOUD_EVENT_SINK.tryEmitNext(cloudEvent);
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
            Integer serviceId = routingMetaData.serviceId();
            BrokerResponder targetResponder;
            RSocket rsocket = null;
            Exception error = null;

            String endpoint = routingMetaData.getEndpoint();
            if (StringUtils.isNotBlank(endpoint)) {
                targetResponder = findDestinationWithEndpoint(endpoint, serviceId);
                if (targetResponder == null) {
                    error = new InvalidException(String.format("Service not found with endpoint '%s' '%s'", gsv, endpoint));
                }
            } else {
                targetResponder = serviceManager.getByServiceId(serviceId);
                if (Objects.isNull(targetResponder)) {
                    error = new InvalidException(String.format("Service not found '%s'", gsv));
                }
            }

            if (targetResponder != null) {
                rsocket = targetResponder;
            }
            if (rsocket != null) {
                sink.success(rsocket);
            } else {
                sink.error(new ApplicationErrorException(String.format("Service not found '%s'", gsv), error));
            }
        });
    }

    /**
     * 根据endpoint属性寻找target service instance
     */
    private BrokerResponder findDestinationWithEndpoint(String endpoint, Integer serviceId) {
        if (endpoint.startsWith("id:")) {
            return serviceManager.getByUUID(endpoint.substring(3));
        }
        int endpointHashCode = endpoint.hashCode();
        for (BrokerResponder responder : serviceManager.getAllByServiceId(serviceId)) {
            if (responder.getAppTagsHashCodeSet().contains(endpointHashCode)) {
                return responder;
            }
        }
        return null;
    }
}