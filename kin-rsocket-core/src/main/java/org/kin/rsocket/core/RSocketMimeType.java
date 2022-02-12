package org.kin.rsocket.core;

import com.google.common.base.Preconditions;
import io.rsocket.metadata.WellKnownMimeType;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author huangjianqin
 * @date 2021/3/24
 */
public enum RSocketMimeType {
    /** json */
    JSON("Json", WellKnownMimeType.APPLICATION_JSON),
    /** protobuf */
    PROTOBUF("Protobuf", WellKnownMimeType.APPLICATION_PROTOBUF),
    /** avro */
    AVRO("Avro", WellKnownMimeType.APPLICATION_AVRO),
    /** hessian */
    HESSIAN("Hessian", WellKnownMimeType.APPLICATION_HESSIAN),
    /** text */
    TEXT("Text", WellKnownMimeType.TEXT_PLAIN),
    /** binary */
    BINARY("Binary", WellKnownMimeType.APPLICATION_OCTET_STREAM),
    /** java对象序列化, 使用kryo */
    JAVA_OBJECT("Java_Object", WellKnownMimeType.APPLICATION_JAVA_OBJECT),
    /** cbor, 物联网专用, 编码紧凑, 轻量, 并且兼容JSON */
    CBOR("CBOR", WellKnownMimeType.APPLICATION_CBOR),
    /** json形式的cloud event数据 */
    CLOUD_EVENTS_JSON("CloudEventsJson", WellKnownMimeType.APPLICATION_CLOUDEVENTS_JSON),
    /** application元数据 */
    APPLICATION("Meta-Application", WellKnownMimeType.MESSAGE_RSOCKET_APPLICATION),
    /** cache控制元数据 */
    CACHE_CONTROL("Meta-CacheControl", WellKnownMimeType.MESSAGE_RSOCKET_DATA_CACHE_CONTROL),
    /** service注册元数据 */
    SERVICE_REGISTRY("Meta-Service-Registry", WellKnownMimeType.MESSAGE_RSOCKET_SERVICE_REGISTRY),
    /** token */
    BEARER_TOKEN("Meta-BearerToken", WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION),
    /** trace */
    TRACING("Meta-Tracing", WellKnownMimeType.MESSAGE_RSOCKET_TRACING_ZIPKIN),
    /** 路由元数据 */
    ROUTING("Meta-Routing", WellKnownMimeType.MESSAGE_RSOCKET_ROUTING),
    /** 二进制形式的路由元数据 */
    BINARY_ROUTING("Meta-BinaryRouting", WellKnownMimeType.MESSAGE_RSOCKET_BINARY_ROUTING),
    /** 消息编码 */
    MESSAGE_MIME_TYPE("Message-MimeType", WellKnownMimeType.MESSAGE_RSOCKET_MIMETYPE),
    /** 消息可接受的编码类型 */
    MESSAGE_ACCEPT_MIME_TYPES("Message-Accept-MimeTypes", WellKnownMimeType.MESSAGE_RSOCKET_ACCEPT_MIMETYPES),
    /** 混合元数据 */
    COMPOSITE_METADATA("Meta-Composite", WellKnownMimeType.MESSAGE_RSOCKET_COMPOSITE_METADATA),
    /** message tags */
    MESSAGE_TAGS("Message-Tags", WellKnownMimeType.MESSAGE_RSOCKET_MESSAGE_TAGS),
    /** message origin */
    MESSAGE_ORIGIN("Message-Origin", WellKnownMimeType.MESSAGE_RSOCKET_MESSAGE_ORIGIN);

    /** key -> id, value -> mime type */
    public static final Map<Byte, RSocketMimeType> ID_2_MIME_TYPE_MAP;
    /** key -> type, value -> mime type */
    public static final Map<String, RSocketMimeType> TYPE_2_MIME_TYPE_MAP;
    public static final Set<RSocketMimeType> ENCODING_MIME_TYPES;

    static {
        ID_2_MIME_TYPE_MAP = Stream.of(RSocketMimeType.values()).collect(
                Collectors.toMap(RSocketMimeType::getId, x -> x));
        TYPE_2_MIME_TYPE_MAP = Stream.of(RSocketMimeType.values()).collect(
                Collectors.toMap(RSocketMimeType::getType, x -> x));
        ENCODING_MIME_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(JSON, PROTOBUF, AVRO, HESSIAN, TEXT, BINARY, JAVA_OBJECT, CBOR)));
    }

    /** {@link WellKnownMimeType#getIdentifier()} */
    private final byte id;
    private final String name;
    /** {@link WellKnownMimeType#getString()} */
    private final String type;

    RSocketMimeType(byte id, String name, String type) {
        this.id = id;
        this.name = name;
        this.type = type;
    }

    RSocketMimeType(String name, WellKnownMimeType type) {
        this.id = type.getIdentifier();
        this.type = type.getString();
        this.name = name;
    }

    /**
     * 根据id寻找{@link RSocketMimeType}
     */
    public static RSocketMimeType getById(byte id) {
        return ID_2_MIME_TYPE_MAP.get(id);
    }

    /**
     * 根据type str寻找{@link RSocketMimeType}
     */
    public static RSocketMimeType getByType(String type) {
        return TYPE_2_MIME_TYPE_MAP.get(type);
    }

    /** rsocket通信默认编码类型 */
    public static RSocketMimeType defaultEncodingType() {
        return JSON;
    }

    /**
     * 是否是编码类型的{@link RSocketMimeType}
     */
    public static boolean isEncodingMimeType(RSocketMimeType mimeType) {
        return ENCODING_MIME_TYPES.contains(mimeType);
    }

    /**
     * 检查是否是编码类型的{@link RSocketMimeType}, 如果不是则报错
     */
    public static void checkEncodingMimeType(RSocketMimeType mimeType) {
        Preconditions.checkArgument(Objects.nonNull(mimeType), "result encoding rsocket mime type is null");
        Preconditions.checkArgument(RSocketMimeType.isEncodingMimeType(mimeType), String.format("unknown rsocket mime type '%s'", mimeType.getType()));
    }

    //getter
    public byte getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }
}
