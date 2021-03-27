package org.kin.rsocket.core.metadata;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author huangjianqin
 * @date 2021/3/24
 */
public enum RSocketMimeType {
    /**
     *
     */
    Json("Json", WellKnownMimeType.APPLICATION_JSON),
    /**
     *
     */
    Protobuf("Protobuf", WellKnownMimeType.APPLICATION_PROTOBUF),
    /**
     *
     */
    Avro("Avro", WellKnownMimeType.APPLICATION_AVRO),
    /**
     *
     */
    Hessian("Hessian", WellKnownMimeType.APPLICATION_HESSIAN),
    /**
     *
     */
    Text("Text", WellKnownMimeType.TEXT_PLAIN),
    /**
     *
     */
    Binary("Binary", WellKnownMimeType.APPLICATION_OCTET_STREAM),
    /**
     *
     */
    Java_Object("JavaObject", WellKnownMimeType.APPLICATION_JAVA_OBJECT),
    /**
     *
     */
    CBOR("CBOR", WellKnownMimeType.APPLICATION_CBOR),
    /**
     *
     */
    CloudEventsJson("CloudEventsJson", WellKnownMimeType.APPLICATION_CLOUDEVENTS_JSON),
    /**
     *
     */
    Application("Meta-Application", WellKnownMimeType.MESSAGE_RSOCKET_APPLICATION),
    /**
     *
     */
    CacheControl("Meta-CacheControl", WellKnownMimeType.MESSAGE_RSOCKET_DATA_CACHE_CONTROL),
    /**
     *
     */
    ServiceRegistry("Meta-Service-Registry", WellKnownMimeType.MESSAGE_RSOCKET_SERVICE_REGISTRY),
    /**
     *
     */
    BearerToken("Meta-BearerToken", WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION),
    /**
     *
     */
    Tracing("Meta-Tracing", WellKnownMimeType.MESSAGE_RSOCKET_TRACING_ZIPKIN),
    /**
     *
     */
    Routing("Meta-Routing", WellKnownMimeType.MESSAGE_RSOCKET_ROUTING),
    /**
     *
     */
    BinaryRouting("Meta-BinaryRouting", WellKnownMimeType.MESSAGE_RSOCKET_BINARY_ROUTING),
    /**
     *
     */
    MessageMimeType("Message-MimeType", WellKnownMimeType.MESSAGE_RSOCKET_MIMETYPE),
    /**
     *
     */
    MessageAcceptMimeTypes("Message-Accept-MimeTypes", WellKnownMimeType.MESSAGE_RSOCKET_ACCEPT_MIMETYPES),
    /**
     *
     */
    CompositeMetadata("Meta-Composite", WellKnownMimeType.MESSAGE_RSOCKET_COMPOSITE_METADATA),
    /**
     *
     */
    MessageTags("Message-Tags", WellKnownMimeType.MESSAGE_RSOCKET_MESSAGE_TAGS),
    /**
     *
     */
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

    public static RSocketMimeType getById(byte id) {
        return MIME_TYPE_MAP.get(id);
    }

    public static RSocketMimeType getByType(String type) {
        return MIME_MIME_MAP.get(type);
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
