package org.kin.rsocket.core;

import io.rsocket.metadata.WellKnownMimeType;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author huangjianqin
 * @date 2021/3/24
 */
public enum RSocketMimeType {
    /** json */
    Json("Json", WellKnownMimeType.APPLICATION_JSON),
    /** protobuf */
    Protobuf("Protobuf", WellKnownMimeType.APPLICATION_PROTOBUF),
    /** avro */
    Avro("Avro", WellKnownMimeType.APPLICATION_AVRO),
    /** hessian */
    Hessian("Hessian", WellKnownMimeType.APPLICATION_HESSIAN),
    /** text */
    Text("Text", WellKnownMimeType.TEXT_PLAIN),
    /** binary */
    Binary("Binary", WellKnownMimeType.APPLICATION_OCTET_STREAM),
    /** java对象序列化, 使用kryo */
    Java_Object("JavaObject", WellKnownMimeType.APPLICATION_JAVA_OBJECT),
    /** cbor, 物联网专用, 编码紧凑, 轻量, 并且兼容JSON */
    CBOR("CBOR", WellKnownMimeType.APPLICATION_CBOR),
    /** json形式的cloud event数据 */
    CloudEventsJson("CloudEventsJson", WellKnownMimeType.APPLICATION_CLOUDEVENTS_JSON),
    /** application元数据 */
    Application("Meta-Application", WellKnownMimeType.MESSAGE_RSOCKET_APPLICATION),
    /** cache控制元数据 */
    CacheControl("Meta-CacheControl", WellKnownMimeType.MESSAGE_RSOCKET_DATA_CACHE_CONTROL),
    /** service注册元数据 */
    ServiceRegistry("Meta-Service-Registry", WellKnownMimeType.MESSAGE_RSOCKET_SERVICE_REGISTRY),
    /** token */
    BearerToken("Meta-BearerToken", WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION),
    /** trace */
    Tracing("Meta-Tracing", WellKnownMimeType.MESSAGE_RSOCKET_TRACING_ZIPKIN),
    /** 路由元数据 */
    Routing("Meta-Routing", WellKnownMimeType.MESSAGE_RSOCKET_ROUTING),
    /** 二进制形式的路由元数据 */
    BinaryRouting("Meta-BinaryRouting", WellKnownMimeType.MESSAGE_RSOCKET_BINARY_ROUTING),
    /** 消息编码 */
    MessageMimeType("Message-MimeType", WellKnownMimeType.MESSAGE_RSOCKET_MIMETYPE),
    /** 消息可接受的编码类型 */
    MessageAcceptMimeTypes("Message-Accept-MimeTypes", WellKnownMimeType.MESSAGE_RSOCKET_ACCEPT_MIMETYPES),
    /** 混合元数据 */
    CompositeMetadata("Meta-Composite", WellKnownMimeType.MESSAGE_RSOCKET_COMPOSITE_METADATA),
    /** message tags */
    MessageTags("Message-Tags", WellKnownMimeType.MESSAGE_RSOCKET_MESSAGE_TAGS),
    /** message origin */
    MessageOrigin("Message-Origin", WellKnownMimeType.MESSAGE_RSOCKET_MESSAGE_ORIGIN);

    /** key -> id, value -> mime type */
    public static final Map<Byte, RSocketMimeType> MIME_TYPE_MAP;
    /** key -> type, value -> mime type */
    public static final Map<String, RSocketMimeType> MIME_MIME_MAP;

    static {
        MIME_TYPE_MAP = Stream.of(RSocketMimeType.values()).collect(
                Collectors.toMap(RSocketMimeType::getId, x -> x));
        MIME_MIME_MAP = Stream.of(RSocketMimeType.values()).collect(
                Collectors.toMap(RSocketMimeType::getType, x -> x));
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
        return MIME_TYPE_MAP.get(id);
    }

    /**
     * 根据type str寻找{@link RSocketMimeType}
     */
    public static RSocketMimeType getByType(String type) {
        return MIME_MIME_MAP.get(type);
    }

    /** rsocket通信默认编码类型 todo 优化:考虑换个地方定义?? */
    public static RSocketMimeType defaultEncodingType() {
        return Json;
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
