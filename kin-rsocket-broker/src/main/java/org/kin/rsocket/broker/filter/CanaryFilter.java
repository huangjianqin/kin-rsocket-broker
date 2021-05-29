package org.kin.rsocket.broker.filter;

import org.kin.rsocket.broker.AbstractRSocketFilter;
import org.kin.rsocket.broker.RSocketFilterContext;
import org.kin.rsocket.broker.RSocketServiceManager;
import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.metadata.GSVRoutingMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 内置金丝雀filter
 * 指定服务还处于灰度版本,
 * TODO 后面考虑用过后台管理, 实现用户自定义金丝雀版本及流量, 而不用修改代码
 *
 * @author huangjianqin
 * @date 2021/5/29
 */
public class CanaryFilter extends AbstractRSocketFilter {
    /** 默认金丝雀版本 */
    public static final String DEFAULT_VERSION = "canary";
    /** 金丝雀版本流量 */
    public static final int DEFAULT_TRAFFIC_RATING = 30;

    @Autowired
    private final RSocketServiceManager serviceManager;
    /** 该filter生效的service name */
    private final List<String> canaryServices;
    /** 金丝雀版本 */
    private final String canaryVersion;
    /** 流量, X%的请求会路由到指定金丝雀版本 */
    private final int trafficRating;
    /** 当前自增index */
    private int roundRobinIndex = 0;

    public CanaryFilter(RSocketServiceManager serviceManager, List<String> canaryServices) {
        this(serviceManager, canaryServices, DEFAULT_VERSION, DEFAULT_TRAFFIC_RATING);
    }

    public CanaryFilter(RSocketServiceManager serviceManager, List<String> canaryServices, int trafficRating) {
        this(serviceManager, canaryServices, DEFAULT_VERSION, trafficRating);
    }

    public CanaryFilter(RSocketServiceManager serviceManager, List<String> canaryServices, String canaryVersion, int trafficRating) {
        this.serviceManager = serviceManager;
        this.canaryServices = canaryServices;
        this.canaryVersion = canaryVersion;
        this.trafficRating = trafficRating;
    }

    @Override
    public Mono<Boolean> shouldFilter(RSocketFilterContext exchange) {
        GSVRoutingMetadata routingMetadata = exchange.getRoutingMetadata();
        //判断是否是金丝雀服务
        if (canaryServices.contains(routingMetadata.getService())) {
            String canaryRouting = ServiceLocator.gsv(routingMetadata.getGroup(), routingMetadata.getService(), canaryVersion);
            if (serviceManager.getByServiceId(ServiceLocator.serviceHashCode(canaryRouting)) != null) {
                return Mono.just(true);
            }
        }
        return Mono.just(false);
    }

    @Override
    public Mono<Void> filter(RSocketFilterContext exchange) {
        if (roundRobinIndex % 100 < trafficRating) {
            GSVRoutingMetadata routingMetadata = exchange.getRoutingMetadata();
            routingMetadata.setVersion(canaryVersion);
        }
        roundRobinIndex = (roundRobinIndex + 1) % 100;
        return Mono.empty();
    }

    @Override
    public String name() {
        return "rsocket canary filter -- " + trafficRating + "% traffic to '" + canaryVersion + "' version";
    }
}
