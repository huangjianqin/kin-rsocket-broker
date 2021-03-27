package org.kin.rsocket.core.event;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.data.PojoCloudEventData;
import org.kin.framework.utils.JSON;
import org.kin.framework.utils.NetUtils;
import org.kin.rsocket.core.RSocketAppContext;
import org.kin.rsocket.core.metadata.WellKnownMimeType;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * cloud event默认基于json数据传输
 *
 * @author huangjianqin
 * @date 2021/3/23
 */
public class CloudEventBuilder<T> {
    private final io.cloudevents.core.builder.CloudEventBuilder builder = io.cloudevents.core.builder.CloudEventBuilder.v1().withDataContentType(WellKnownMimeType.APPLICATION_JSON.getString());
    private T data;
    private static URI DEFAULT_SOURCE = URI.create("app://" + NetUtils.getIp() + "/" + "?id=" + RSocketAppContext.ID);

    /**
     * Gets a brand new builder instance
     *
     * @param <T> The 'data' type
     */
    public static <T> CloudEventBuilder<T> builder() {
        return new CloudEventBuilder<>();
    }

    /**
     * builder with UUID, application/json, now timestamp, Class full name as type and default sources
     *
     * @param data data
     * @param <T>  data type
     * @return cloud event builder
     */
    public static <T> CloudEventBuilder<T> builder(T data) {
        CloudEventBuilder<T> builder = new CloudEventBuilder<>();
        builder.data = data;
        builder
                .withId(UUID.randomUUID().toString())
                .withDataContentType(WellKnownMimeType.APPLICATION_JSON.getString())
                .withTime(OffsetDateTime.now())
                .withType(data.getClass().getCanonicalName())
                .withSource(DEFAULT_SOURCE);
        return builder;
    }

    public CloudEventBuilder<T> withId(String id) {
        this.builder.withId(id);
        return this;
    }

    public CloudEventBuilder<T> withSource(URI source) {
        this.builder.withSource(source);
        return this;
    }

    public CloudEventBuilder<T> withType(String type) {
        this.builder.withType(type);
        return this;
    }

    public CloudEventBuilder<T> withDataschema(URI dataschema) {
        this.builder.withDataSchema(dataschema);
        return this;
    }

    public CloudEventBuilder<T> withDataContentType(String datacontenttype) {
        this.builder.withDataContentType(datacontenttype);
        return this;
    }

    public CloudEventBuilder<T> withSubject(String subject) {
        this.builder.withSubject(subject);
        return this;
    }

    public CloudEventBuilder<T> withTime(OffsetDateTime time) {
        this.builder.withTime(time);
        return this;
    }

    public CloudEventBuilder<T> withData(T data) {
        this.data = data;
        return this;
    }

    public CloudEventData<T> build() {
        CloudEvent cloudEvent = builder.withData(PojoCloudEventData.wrap(this.data, JSON::writeBytes)).build();
        return new CloudEventData<>(data, cloudEvent);
    }
}
