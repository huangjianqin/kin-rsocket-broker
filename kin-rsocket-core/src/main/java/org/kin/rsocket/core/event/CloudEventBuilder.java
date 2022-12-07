package org.kin.rsocket.core.event;

import com.google.common.base.Preconditions;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.data.PojoCloudEventData;
import io.rsocket.metadata.WellKnownMimeType;
import org.kin.framework.utils.JSON;
import org.kin.framework.utils.NetUtils;
import org.kin.rsocket.core.RSocketAppContext;

import javax.annotation.Nonnull;
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
    /**  */
    private static final URI DEFAULT_SOURCE = URI.create("app://" + RSocketAppContext.ID + "?ip=" + NetUtils.getIp());
    /** cloud event builder预设 */
    private final io.cloudevents.core.builder.CloudEventBuilder builder =
            io.cloudevents.core.builder.CloudEventBuilder
                    .v1()
                    .withDataContentType(WellKnownMimeType.APPLICATION_JSON.getString());
    /** 实际event实例 */
    private T data;

    /**
     * 空builder
     *
     * @param <T> cloud event data type
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
    public static <T> CloudEventBuilder<T> builder(@Nonnull T data) {
        Preconditions.checkNotNull(data);

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

    /**
     * event id
     *
     * @param id event id
     * @return builder
     */
    public CloudEventBuilder<T> id(String id) {
        this.builder.withId(id);
        return this;
    }

    /**
     * event source
     *
     * @param source event source
     * @return builder
     */
    public CloudEventBuilder<T> source(URI source) {
        this.builder.withSource(source);
        return this;
    }

    /**
     * event data type
     *
     * @param type event data type
     * @return builder
     */
    public CloudEventBuilder<T> type(String type) {
        this.builder.withType(type);
        return this;
    }

    /**
     * event schema
     *
     * @param dataSchema event schema
     * @return builder
     */
    public CloudEventBuilder<T> dataSchema(URI dataSchema) {
        this.builder.withDataSchema(dataSchema);
        return this;
    }

    /**
     * event date 内容类型(即序列化后的类型)
     *
     * @param dataContentType event date 内容类型(即序列化后的类型)
     * @return builder
     */
    public CloudEventBuilder<T> dataContentType(String dataContentType) {
        this.builder.withDataContentType(dataContentType);
        return this;
    }

    /**
     * event 主题
     *
     * @param subject event 主题
     * @return builder
     */
    public CloudEventBuilder<T> subject(String subject) {
        this.builder.withSubject(subject);
        return this;
    }

    /**
     * event trigger time
     *
     * @param time event trigger time
     * @return builder
     */
    public CloudEventBuilder<T> time(OffsetDateTime time) {
        this.builder.withTime(time);
        return this;
    }

    /**
     * event data
     *
     * @param data event data
     * @return builder
     */
    public CloudEventBuilder<T> data(T data) {
        this.data = data;
        return this;
    }

    /**
     * 添加event extension, 额外kv
     *
     * @param key   event extension key
     * @param value event extension value
     * @return builder
     */
    public CloudEventBuilder<T> extension(@Nonnull String key, @Nonnull String value) {
        this.builder.withExtension(key, value);
        return this;
    }

    /**
     * @return {@link CloudEvent}实例
     */
    public CloudEvent build() {
        return builder.withData(PojoCloudEventData.wrap(this.data, JSON::writeBytes)).build();
    }
}
