package org.kin.rsocket.service;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import io.rsocket.frame.FrameType;
import org.kin.framework.utils.CollectionUtils;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.core.RSocketMimeType;
import org.kin.rsocket.core.ReactiveMethodSupport;
import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.ServiceMapping;
import org.kin.rsocket.core.metadata.*;
import org.kin.rsocket.core.utils.MurmurHash3;
import org.kin.rsocket.core.utils.Separators;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.Arrays;

/**
 * 服务接口方法元数据
 *
 * @author huangjianqin
 * @date 2021/3/26
 */
final class ReactiveMethodMetadata extends ReactiveMethodSupport {
    /** service full name {@link Method#getName()} */
    private String service;
    /** handler name, 默认=method name */
    private String handler;
    /** full name, service and name */
    private String fullName;
    /** service group */
    private String group;
    /** service version */
    private String version;
    /** service ID */
    private Integer serviceId;
    /** method handler id */
    private Integer handlerId;
    /** endpoint */
    private String endpoint;
    /** sticky */
    private boolean sticky;
    /** rsocket frame type */
    private FrameType rsocketFrameType;
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
        this.service = service;
        handler = method.getName();
        this.dataEncodingType = dataEncodingType;
        this.acceptEncodingTypes = acceptEncodingTypes;
        this.group = group;
        this.version = version;
        this.endpoint = endpoint;

        //处理method上的@ServiceMapping注解
        ServiceMapping serviceMapping = method.getAnnotation(ServiceMapping.class);
        if (serviceMapping != null) {
            initServiceMapping(serviceMapping);
        }

        // sticky from service builder or @ServiceMapping
        this.sticky = sticky | this.sticky;

        fullName = this.service + Separators.SERVICE_HANDLER + this.handler;
        serviceId = MurmurHash3.hash32(ServiceLocator.gsv(this.group, this.service, this.version));
        handlerId = MurmurHash3.hash32(service + Separators.SERVICE_HANDLER + handler);
        //byte buffer binary encoding
        if (paramCount == 1) {
            Class<?> parameterType = method.getParameterTypes()[0];
            if (BINARY_CLASS_LIST.contains(parameterType)) {
                this.dataEncodingType = RSocketMimeType.Binary;
            }
        }
        //初始化方法调用的元数据
        initCompositeMetadata(origin);
        //检查第一二个参数是否是Flux
        if (paramCount == 1 && Flux.class.isAssignableFrom(method.getParameterTypes()[0])) {
            rsocketFrameType = FrameType.REQUEST_CHANNEL;
        } else if (paramCount == 2 && Flux.class.isAssignableFrom(method.getParameterTypes()[1])) {
            rsocketFrameType = FrameType.REQUEST_CHANNEL;
        }
        if (rsocketFrameType != null && rsocketFrameType == FrameType.REQUEST_CHANNEL) {
            if (Mono.class.isAssignableFrom(returnType)) {
                monoChannel = true;
            }
        }
        //参数不含Flux
        if (rsocketFrameType == null) {
            if (returnType.equals(Void.TYPE) || (returnType.equals(Mono.class) && inferredClassForReturn.equals(Void.TYPE))) {
                rsocketFrameType = FrameType.REQUEST_FNF;
            } else if (Flux.class.isAssignableFrom(returnType)) {
                rsocketFrameType = FrameType.REQUEST_STREAM;
            } else {
                rsocketFrameType = FrameType.REQUEST_RESPONSE;
            }
        }
    }

    /**
     * 解析method上的{@link ServiceMapping}注解
     */
    private void initServiceMapping(ServiceMapping serviceMapping) {
        if (!serviceMapping.value().isEmpty()) {
            String serviceName = serviceMapping.value();
            if (serviceName.contains(Separators.SERVICE_HANDLER)) {
                service = serviceName.substring(0, serviceName.lastIndexOf(Separators.SERVICE_HANDLER));
                handler = serviceName.substring(serviceName.lastIndexOf(Separators.SERVICE_HANDLER) + 1);
            } else {
                handler = serviceName;
            }
        }
        if (!serviceMapping.group().isEmpty()) {
            group = serviceMapping.group();
        }
        if (!serviceMapping.version().isEmpty()) {
            version = serviceMapping.version();
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
    private void initCompositeMetadata(URI origin) {
        //routing metadata
        GSVRoutingMetadata routingMetadata = GSVRoutingMetadata.of(group, service, handler, version, endpoint, sticky);

        //binary routing metadata
        BinaryRoutingMetadata binaryRoutingMetadata = BinaryRoutingMetadata.of(routingMetadata.genRoutingKey());

        //encoding mime type
        MessageMimeTypeMetadata messageMimeTypeMetadata = MessageMimeTypeMetadata.of(dataEncodingType);

        //accepted mimetype
        MessageAcceptMimeTypesMetadata messageAcceptMimeTypesMetadata = MessageAcceptMimeTypesMetadata.of(acceptEncodingTypes);

        //origin metadata
        OriginMetadata originMetadata = OriginMetadata.of(origin);

        //default composite metadata
        CompositeByteBuf compositeMetadataBytes;
        compositeMetadata = RSocketCompositeMetadata.of(routingMetadata, messageMimeTypeMetadata, messageAcceptMimeTypesMetadata, originMetadata);
        //add gsv routing data if endpoint not empty
        if (StringUtils.isNotBlank(endpoint)) {
            this.compositeMetadata.addMetadata(binaryRoutingMetadata);
            compositeMetadataBytes = (CompositeByteBuf) this.compositeMetadata.getContent();
        } else {
            compositeMetadataBytes = (CompositeByteBuf) this.compositeMetadata.getContent();
            //add BinaryRoutingMetadata as first
            compositeMetadataBytes.addComponent(true, 0, binaryRoutingMetadata.getHeaderAndContent());
        }

        //cache
        this.compositeMetadataBytes = Unpooled.copiedBuffer(compositeMetadataBytes);
        ReferenceCountUtil.safeRelease(compositeMetadataBytes);
    }

    //getter
    public String getService() {
        return service;
    }

    public String getHandler() {
        return handler;
    }

    public String getFullName() {
        return fullName;
    }

    public String getGroup() {
        return group;
    }

    public String getVersion() {
        return version;
    }

    public Integer getServiceId() {
        return serviceId;
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

    public FrameType getRsocketFrameType() {
        return rsocketFrameType;
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
}
