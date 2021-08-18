package org.kin.rsocket.broker;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.core.MetricsNames;
import org.kin.rsocket.core.metadata.BinaryRoutingMetadata;
import org.kin.rsocket.core.metadata.GSVRoutingMetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author huangjianqin
 * @date 2021/8/18
 */
public final class MetricsUtils {
    private MetricsUtils() {
    }

    public static void metrics(GSVRoutingMetadata routingMetadata, String frameType) {
        List<Tag> tags = new ArrayList<>();
        String group = routingMetadata.getGroup();
        if (StringUtils.isNotBlank(group)) {
            tags.add(Tag.of("group", group));
        }
        String version = routingMetadata.getVersion();
        if (StringUtils.isNotBlank(version)) {
            tags.add(Tag.of("version", version));
        }
        tags.add(Tag.of("method", routingMetadata.getHandler()));
        tags.add(Tag.of("frame", frameType));
        String service = routingMetadata.getService();
        //具体某一服务请求的数量
        Metrics.counter(service.concat(MetricsNames.COUNT_SUFFIX), tags).increment();
        //broker接受upstream服务请求数量
        Metrics.counter(MetricsNames.RSOCKET_REQUEST_COUNT).increment();
        //某一服务(不区分group和version)所有请求的数量
        Metrics.counter(service.concat(MetricsNames.COUNT_SUFFIX)).increment();
    }

    /**
     * TODO 看看使用快速路由的时候能不能把服务信息带过来
     */
    public static void metrics(BinaryRoutingMetadata binaryRoutingMetadata, String frameType) {
        //具体某一服务接口方法请求的数量
        Metrics.counter(binaryRoutingMetadata.getHandlerId() + MetricsNames.COUNT_SUFFIX, Collections.singletonList(Tag.of("frame", frameType))).increment();
        //broker接受upstream服务请求数量
        Metrics.counter(MetricsNames.RSOCKET_REQUEST_COUNT).increment();

    }
}
