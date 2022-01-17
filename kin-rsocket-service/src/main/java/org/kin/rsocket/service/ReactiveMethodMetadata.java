package org.kin.rsocket.service;

import io.micrometer.core.instrument.Tag;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import io.rsocket.frame.FrameType;
import org.kin.framework.utils.CollectionUtils;
import org.kin.framework.utils.MurmurHash3;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.core.RSocketMimeType;
import org.kin.rsocket.core.ReactiveMethodSupport;
import org.kin.rsocket.core.ServiceMapping;
import org.kin.rsocket.core.metadata.*;
import org.kin.rsocket.core.utils.Separators;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 服务接口方法元数据
 *
 * @author huangjianqin
 * @date 2021/3/26
 */
final class ReactiveMethodMetadata extends ReactiveMethodSupport {
    /** handler name, 默认=method name */
    private String handler;
    /** handler id string */
    private final String handlerIdStr;
    /** method handler id */
    private final Integer handlerId;
    /** endpoint */
    private String endpoint;
    /** sticky */
    private boolean sticky;
    /** rsocket frame type */
    private FrameType frameType;
    /** parameters encoding type */
    private RSocketMimeType dataEncodingType;
    /** accept encoding */
    private RSocketMimeType[] acceptEncodingTypes;
    /** default composite metadata for RSocket, and include routing, encoding & accept encoding */
    private RSocketCompositeMetadata compositeMetadata;
    /** bytebuf for default composite metadata, 直接remote call, 不用每次call时才生成, 提高效率 */
    private ByteBuf compositeMetadataBytes;
    /** 返回值是否是{@link Mono} */
    private boolean monoChannel = false;
    /** metrics tags */
    private final List<Tag> metricsTags = new ArrayList<>();

    ReactiveMethodMetadata(String group,
                           String service,
                           String version,
                           Method method,
                           RSocketMimeType dataEncodingType,
                           RSocketMimeType[] acceptEncodingTypes,
                           String endpoint,
                           boolean sticky,
                           URI origin) {
        super(method);
        handler = method.getName();
        this.dataEncodingType = dataEncodingType;
        this.acceptEncodingTypes = acceptEncodingTypes;
        this.endpoint = endpoint;

        //处理method上的@ServiceMapping注解
        ServiceMapping serviceMapping = method.getAnnotation(ServiceMapping.class);
        if (serviceMapping != null) {
            initServiceMapping(serviceMapping);
        }

        // sticky from service builder or @ServiceMapping
        this.sticky = sticky | this.sticky;

        handlerIdStr = service + Separators.SERVICE_HANDLER + this.handler;
        handlerId = MurmurHash3.hash32(handlerIdStr);
        //byte buffer binary encoding
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (paramCount == 1) {
            Class<?> parameterType = parameterTypes[0];
            if (BINARY_CLASS_LIST.contains(parameterType)) {
                this.dataEncodingType = RSocketMimeType.BINARY;
            }
        }
        //初始化方法调用的元数据
        initCompositeMetadata(origin, group, service, version);
        //检查第一二个参数是否是Flux
        if (paramCount == 1 && Flux.class.isAssignableFrom(parameterTypes[0])) {
            frameType = FrameType.REQUEST_CHANNEL;
        } else if (paramCount == 2 && Flux.class.isAssignableFrom(parameterTypes[1])) {
            frameType = FrameType.REQUEST_CHANNEL;
        }
        if (frameType != null && frameType == FrameType.REQUEST_CHANNEL) {
            if (Mono.class.isAssignableFrom(returnType)) {
                monoChannel = true;
            }
        }
        //参数不含Flux
        if (frameType == null) {
            if (Void.TYPE.equals(returnType) || Void.class.equals(returnType) ||
                    (returnType.equals(Mono.class) && (Void.TYPE.equals(inferredClassForReturn) || Void.class.equals(inferredClassForReturn)))) {
                frameType = FrameType.REQUEST_FNF;
            } else if (Flux.class.isAssignableFrom(returnType)) {
                frameType = FrameType.REQUEST_STREAM;
            } else {
                frameType = FrameType.REQUEST_RESPONSE;
            }
        }

        //metrics tags for micrometer
        if (StringUtils.isNotBlank(group)) {
            metricsTags.add(Tag.of("group", group));
        }
        if (StringUtils.isNotBlank(version)) {
            metricsTags.add(Tag.of("version", version));
        }
        metricsTags.add(Tag.of("method", this.handler));
        metricsTags.add(Tag.of("frame", this.frameType.name()));
    }

    /**
     * 解析method上的{@link ServiceMapping}注解
     */
    private void initServiceMapping(ServiceMapping serviceMapping) {
        if (StringUtils.isNotBlank(serviceMapping.value())) {
            handler = serviceMapping.value();
        }
        if (!serviceMapping.endpoint().isEmpty()) {
            endpoint = serviceMapping.endpoint();
        }
        if (!serviceMapping.paramEncoding().isEmpty()) {
            dataEncodingType = RSocketMimeType.getByType(serviceMapping.paramEncoding());
        }
        if (CollectionUtils.isNonEmpty(serviceMapping.resultEncoding())) {
            acceptEncodingTypes = Arrays.stream(serviceMapping.resultEncoding()).map(RSocketMimeType::getByType).toArray(RSocketMimeType[]::new);
        }
        sticky = serviceMapping.sticky();
    }

    /**
     * 初始化方法调用的元数据
     */
    private void initCompositeMetadata(URI origin, String group, String service, String version) {
        //routing metadata
        GSVRoutingMetadata routingMetadata = GSVRoutingMetadata.of(group, service, handler, version, endpoint, sticky);

        //encoding mime type
        MessageMimeTypeMetadata messageMimeTypeMetadata = MessageMimeTypeMetadata.of(dataEncodingType);

        //accepted mimetype
        MessageAcceptMimeTypesMetadata messageAcceptMimeTypesMetadata = MessageAcceptMimeTypesMetadata.of(acceptEncodingTypes);

        //origin metadata
        OriginMetadata originMetadata = OriginMetadata.of(origin);

        //default composite metadata
        compositeMetadata = RSocketCompositeMetadata.of(routingMetadata, messageMimeTypeMetadata, messageAcceptMimeTypesMetadata, originMetadata);
        CompositeByteBuf compositeMetadataBytes = (CompositeByteBuf) this.compositeMetadata.getContent();

        if (StringUtils.isBlank(endpoint)) {
            //使用快速路由
            //binary routing metadata
            BinaryRoutingMetadata binaryRoutingMetadata = BinaryRoutingMetadata.of(routingMetadata);

            //add BinaryRoutingMetadata as first
            compositeMetadataBytes.addComponent(true, 0, binaryRoutingMetadata.getHeaderAndContent());
        }

        //cache
        this.compositeMetadataBytes = Unpooled.copiedBuffer(compositeMetadataBytes);
        ReferenceCountUtil.safeRelease(compositeMetadataBytes);
    }

    /**
     * 方法是否返回void
     */
    public boolean isReturnVoid() {
        return Void.TYPE.equals(returnType);
    }

    //getter
    public String getHandler() {
        return handler;
    }

    public String getHandlerIdStr() {
        return handlerIdStr;
    }

    public Integer getHandlerId() {
        return handlerId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public boolean isSticky() {
        return sticky;
    }

    public FrameType getFrameType() {
        return frameType;
    }

    public RSocketMimeType getDataEncodingType() {
        return dataEncodingType;
    }

    public RSocketMimeType[] getAcceptEncodingTypes() {
        return acceptEncodingTypes;
    }

    public RSocketCompositeMetadata getCompositeMetadata() {
        return compositeMetadata;
    }

    public ByteBuf getCompositeMetadataBytes() {
        //防止外部误修改
        return compositeMetadataBytes.retainedDuplicate();
    }

    public boolean isMonoChannel() {
        return monoChannel;
    }

    public List<Tag> getMetricsTags() {
        return metricsTags;
    }
}
