package org.kin.rsocket.core.event;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.data.PojoCloudEventData;
import io.rsocket.metadata.WellKnownMimeType;
import org.kin.framework.utils.JSON;
import org.kin.framework.utils.NetUtils;
import org.kin.rsocket.core.RSocketAppContext;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * cloud event默认基于json数据传输
 *
 * @author huangjianqin
 * @date 2021/3/23
 */
public class RSocketCloudEventBuilder<T> {
    private final CloudEventBuilder builder = CloudEventBuilder.v1().withDataContentType(WellKnownMimeType.APPLICATION_JSON.getString());
    private T data;
    private static URI DEFAULT_SOURCE = URI.create("app://" + NetUtils.getIp() + "/" + "?id=" + RSocketAppContext.ID);

    /**
     * Gets a brand new builder instance
     *
     * @param <T> The 'data' type
     */
    public static <T> RSocketCloudEventBuilder<T> builder() {
        return new RSocketCloudEventBuilder<>();
    }

    /**
     * builder with UUID, application/json, now timestamp, Class full name as type and default sources
     *
     * @param data data
     * @param <T>  data type
     * @return cloud event builder
     */
    public static <T> RSocketCloudEventBuilder<T> builder(T data) {
        RSocketCloudEventBuilder<T> builder = new RSocketCloudEventBuilder<>();
        builder.data = data;
        builder
                .withId(UUID.randomUUID().toString())
                .withDataContentType(WellKnownMimeType.APPLICATION_JSON.getString())
                .withTime(OffsetDateTime.now())
                .withType(data.getClass().getCanonicalName())
                .withSource(DEFAULT_SOURCE);
        return builder;
    }

    public RSocketCloudEventBuilder<T> withId(String id) {
        this.builder.withId(id);
        return this;
    }

    public RSocketCloudEventBuilder<T> withSource(URI source) {
        this.builder.withSource(source);
        return this;
    }

    public RSocketCloudEventBuilder<T> withType(String type) {
        this.builder.withType(type);
        return this;
    }

    public RSocketCloudEventBuilder<T> withDataschema(URI dataschema) {
        this.builder.withDataSchema(dataschema);
        return this;
    }

    public RSocketCloudEventBuilder<T> withDataContentType(String datacontenttype) {
        this.builder.withDataContentType(datacontenttype);
        return this;
    }

    public RSocketCloudEventBuilder<T> withSubject(String subject) {
        this.builder.withSubject(subject);
        return this;
    }

    public RSocketCloudEventBuilder<T> withTime(OffsetDateTime time) {
        this.builder.withTime(time);
        return this;
    }

    public RSocketCloudEventBuilder<T> withData(T data) {
        this.data = data;
        return this;
    }

    public CloudEventData<T> build() {
        CloudEvent cloudEvent = builder.withData(PojoCloudEventData.wrap(this.data, JSON::writeBytes)).build();
        return new CloudEventData<>(data, cloudEvent);
    }
}
