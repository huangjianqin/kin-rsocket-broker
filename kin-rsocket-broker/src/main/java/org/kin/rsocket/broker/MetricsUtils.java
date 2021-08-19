package org.kin.rsocket.broker;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.core.MetricsNames;
import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.metadata.GSVRoutingMetadata;

import java.util.ArrayList;
import java.util.List;

/**
 * @author huangjianqin
 * @date 2021/8/18
 */
public final class MetricsUtils {
    private MetricsUtils() {
    }

    private static void metrics(String group, String version, String service, String handler, String frameType) {
        if (StringUtils.isNotBlank(service)) {
            List<Tag> tags = new ArrayList<>();
            if (StringUtils.isNotBlank(group)) {
                tags.add(Tag.of("group", group));
            }
            if (StringUtils.isNotBlank(version)) {
                tags.add(Tag.of("version", version));
            }
            if (StringUtils.isNotBlank(handler)) {
                tags.add(Tag.of("method", handler));
            }
            tags.add(Tag.of("frame", frameType));
            //具体某一服务请求的数量
            Metrics.counter(service.concat(MetricsNames.COUNT_SUFFIX), tags).increment();
            //某一服务(不区分group和version)所有请求的数量
            Metrics.counter(service.concat(MetricsNames.COUNT_SUFFIX)).increment();
        }

        //broker接受upstream服务请求数量
        Metrics.counter(MetricsNames.RSOCKET_REQUEST_COUNT).increment();
    }

    public static void metrics(GSVRoutingMetadata routingMetadata, String frameType) {
        metrics(routingMetadata.getGroup(), routingMetadata.getVersion(), routingMetadata.getService(), routingMetadata.getHandler(), frameType);
    }

    public static void metrics(ServiceLocator serviceLocator, String handler, String frameType) {
        metrics(serviceLocator.getGroup(), serviceLocator.getVersion(), serviceLocator.getService(), handler, frameType);
    }
}
