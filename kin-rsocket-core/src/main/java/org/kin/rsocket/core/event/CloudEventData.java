package org.kin.rsocket.core.event;

import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventAttributes;

import java.util.Optional;

/**
 * CloudEvent封装以及其数据
 *
 * @author huangjianqin
 * @date 2021/3/23
 */
public class CloudEventData<T> {
    private final T data;
    private final CloudEvent cloudEvent;

    public CloudEventData(T data, CloudEvent cloudEvent) {
        this.data = data;
        this.cloudEvent = cloudEvent;
    }

    public Optional<T> getData() {
        return Optional.ofNullable(data);
    }

    public CloudEventAttributes getAttributes() {
        return cloudEvent;
    }

    public CloudEvent getCloudEvent() {
        return cloudEvent;
    }
}
