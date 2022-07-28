package org.kin.rsocket.gateway.grpc;

import io.micrometer.core.instrument.Tag;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import io.rsocket.metadata.WellKnownMimeType;
import org.kin.framework.utils.MurmurHash3;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.core.ReactiveMethodSupport;
import org.kin.rsocket.core.metadata.*;
import org.kin.rsocket.core.utils.Separators;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * reactive grpc method元数据
 *
 * @author huangjianqin
 * @date 2022/1/9
 */
final class ReactiveGrpcMethodMetadata extends ReactiveMethodSupport {
    /** request one response one */
    static final Integer UNARY = 1;
    /** request one response many */
    static final Integer SERVER_STREAMING = 2;
    /** request many response one */
    static final Integer CLIENT_STREAMING = 3;
    /** request many response many */
    static final Integer BIDIRECTIONAL_STREAMING = 4;

    /** grpc类型 */
    private Integer rpcType;
    /** handleId */
    private Integer handlerId;
    /** endpoint */
    private String endpoint;
    /** sticky */
    private boolean sticky;
    /** 服务方法 composite metadata, 直接remote call, 不用每次call时才生成, 提高效率 */
    private ByteBuf compositeMetadataByteBuf;
    /** metrics tags */
    private final List<Tag> metricsTags = new ArrayList<>();

    ReactiveGrpcMethodMetadata(Method method, String group, String service, String version,
                               String endpoint, boolean sticky) {
        super(method);

        //判断grpc请求类型
        Class<?> parameterType = method.getParameterTypes()[0];
        if (parameterType.isAssignableFrom(Mono.class) && returnType.isAssignableFrom(Mono.class)) {
            rpcType = UNARY;
            metricsTags.add(Tag.of("rpcType", "unary"));
        } else if (parameterType.isAssignableFrom(Mono.class) && returnType.isAssignableFrom(Flux.class)) {
            rpcType = SERVER_STREAMING;
            metricsTags.add(Tag.of("rpcType", "server_streaming"));
        } else if (parameterType.isAssignableFrom(Flux.class) && returnType.isAssignableFrom(Mono.class)) {
            rpcType = CLIENT_STREAMING;
            metricsTags.add(Tag.of("rpcType", "client_streaming"));
        } else if (parameterType.isAssignableFrom(Flux.class) && returnType.isAssignableFrom(Flux.class)) {
            rpcType = BIDIRECTIONAL_STREAMING;
            metricsTags.add(Tag.of("rpcType", "bidirectional_streaming"));
        }

        String methodName = method.getName();
        handlerId = MurmurHash3.hash32(service + Separators.SERVICE_HANDLER + methodName);
        this.endpoint = endpoint;
        this.sticky = sticky;

        initCompositeMetadata(group, service, version);

        //metrics tags for micrometer
        if (StringUtils.isNotBlank(group)) {
            metricsTags.add(Tag.of("group", group));
        }
        if (StringUtils.isNotBlank(version)) {
            metricsTags.add(Tag.of("version", version));
        }
        metricsTags.add(Tag.of("method", methodName));
    }

    /**
     * 初始化方法调用的元数据
     */
    private void initCompositeMetadata(String group, String service, String version) {
        //routing metadata
        GSVRoutingMetadata routingMetadata = GSVRoutingMetadata.from(group, service, method.getName(), version, endpoint, sticky);

        //encoding mime type
        MessageMimeTypeMetadata messageMimeTypeMetadata = MessageMimeTypeMetadata.from(WellKnownMimeType.APPLICATION_PROTOBUF);

        //accepted mimetype
        MessageAcceptMimeTypesMetadata messageAcceptMimeTypesMetadata = MessageAcceptMimeTypesMetadata.from(WellKnownMimeType.APPLICATION_PROTOBUF);

        //default composite metadata
        RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.from(routingMetadata, messageMimeTypeMetadata, messageAcceptMimeTypesMetadata);
        CompositeByteBuf compositeMetadataBytes = (CompositeByteBuf) compositeMetadata.getContent();

        if (StringUtils.isBlank(endpoint)) {
            //默认使用快速路由
            //binary routing metadata
            BinaryRoutingMetadata binaryRoutingMetadata = BinaryRoutingMetadata.from(routingMetadata);

            //add BinaryRoutingMetadata as first
            compositeMetadataBytes.addComponent(true, 0, binaryRoutingMetadata.getHeaderAndContent());
        }

        this.compositeMetadataByteBuf = Unpooled.copiedBuffer(compositeMetadataBytes);
        ReferenceCountUtil.safeRelease(compositeMetadataBytes);
    }

    //getter
    Integer getRpcType() {
        return rpcType;
    }

    Integer getHandlerId() {
        return handlerId;
    }

    String getEndpoint() {
        return endpoint;
    }

    boolean isSticky() {
        return sticky;
    }

    List<Tag> getMetricsTags() {
        return metricsTags;
    }

    ByteBuf getCompositeMetadataByteBuf() {
        return compositeMetadataByteBuf.retainedDuplicate();
    }

    String getMethodName() {
        return method.getName();
    }
}
