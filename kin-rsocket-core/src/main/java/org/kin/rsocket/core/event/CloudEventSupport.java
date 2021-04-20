package org.kin.rsocket.core.event;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.kin.framework.utils.JSON;

import java.io.Serializable;
import java.net.URI;
import java.util.Map;

/**
 * @author huangjianqin
 * @date 2021/3/23
 */
@SuppressWarnings("unchecked")
public interface CloudEventSupport extends Serializable {
    /**
     * 解析cloud event数据
     */
    static <T extends CloudEventSupport> T unwrapData(CloudEventData<?> cloudEvent, Class<T> targetClass) {
        return cloudEvent.getData().map(data -> {
            try {
                if (data instanceof ObjectNode || data instanceof Map) {
                    return JSON.convert(data, targetClass);
                } else if (data.getClass().isAssignableFrom(targetClass)) {
                    return (T) data;
                } else if (data instanceof String) {
                    return JSON.read((String) data, targetClass);
                }
            } catch (Exception ignore) {
                //do nothing
            }
            return null;
        }).orElse(null);
    }

    default CloudEventData<? extends CloudEventSupport> toCloudEventData(URI source) {
        return CloudEventBuilder.builder(this).source(source).build();
    }
}

