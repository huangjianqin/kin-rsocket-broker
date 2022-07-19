package org.kin.rsocket.core.event;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.data.PojoCloudEventData;
import io.rsocket.metadata.WellKnownMimeType;
import org.kin.framework.utils.JSON;
import org.kin.framework.utils.NetUtils;
import org.kin.rsocket.core.RSocketAppContext;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * cloud event builder
 * json编码
 *
 * @author huangjianqin
 * @date 2021/3/23
 */
public class CloudEventBuilder<T> {
    private static final URI DEFAULT_SOURCE = URI.create("app://" + RSocketAppContext.ID + "?ip=" + NetUtils.getIp());
    /** 包装的builder */
    private final io.cloudevents.core.builder.CloudEventBuilder builder =
            io.cloudevents.core.builder.CloudEventBuilder
                    .v1()
                    .withDataContentType(WellKnownMimeType.APPLICATION_JSON.getString());
    private T data;

    /**
     * 空builder
     *
     * @param <T> cloud event datat type
     */
    public static <T> CloudEventBuilder<T> builder() {
        return new CloudEventBuilder<>();
    }

    /**
     * 带UUID, application/json, now timestamp, Class full name as type和default sources的builder
     *
     * @param data data
     * @param <T>  cloud event data type
     * @return cloud event builder
     */
    public static <T> CloudEventBuilder<T> builder(T data) {
        CloudEventBuilder<T> builder = new CloudEventBuilder<>();
        builder.data = data;
        builder.id(UUID.randomUUID().toString())
                //目前仅仅只有json编码
                .dataContentType(WellKnownMimeType.APPLICATION_JSON.getString())
                .time(OffsetDateTime.now())
                .type(data.getClass().getName())
                .source(DEFAULT_SOURCE);
        return builder;
    }

    public CloudEventBuilder<T> id(String id) {
        this.builder.withId(id);
        return this;
    }

    public CloudEventBuilder<T> source(URI source) {
        this.builder.withSource(source);
        return this;
    }

    public CloudEventBuilder<T> type(String type) {
        this.builder.withType(type);
        return this;
    }

    public CloudEventBuilder<T> dataSchema(URI dataschema) {
        this.builder.withDataSchema(dataschema);
        return this;
    }

    public CloudEventBuilder<T> dataContentType(String datacontenttype) {
        this.builder.withDataContentType(datacontenttype);
        return this;
    }

    public CloudEventBuilder<T> subject(String subject) {
        this.builder.withSubject(subject);
        return this;
    }

    public CloudEventBuilder<T> time(OffsetDateTime time) {
        this.builder.withTime(time);
        return this;
    }

    public CloudEventBuilder<T> data(T data) {
        this.data = data;
        return this;
    }

    public CloudEventData<T> build() {
        CloudEvent cloudEvent = builder.withData(PojoCloudEventData.wrap(this.data, JSON::writeBytes)).build();
        return new CloudEventData<>(data, cloudEvent);
    }
}
