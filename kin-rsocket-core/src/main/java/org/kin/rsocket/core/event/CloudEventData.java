package org.kin.rsocket.core.event;

import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventAttributes;
import org.kin.framework.utils.StringUtils;

import java.util.Optional;

/**
 * CloudEvent封装以及其数据
 *
 * @author huangjianqin
 * @date 2021/3/23
 */
public class CloudEventData<T> {
    /** 数据 */
    private final T data;
    /** 封装成的cloud event */
    private final CloudEvent cloudEvent;
    /**
     * cloud event 来源
     * <p>
     * 格式: upstream/downstream : app_name : gsv, 例如upstream:broker:*, downstream:app-name:*
     */
    private String source;

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

    public String getSource() {
        return source;
    }

    public void updateSourceIfEmpty(String source) {
        if (StringUtils.isBlank(this.source)) {
            //没有设置, 则设置默认的cloud event 来源
            this.source = source;
        }
    }
}
