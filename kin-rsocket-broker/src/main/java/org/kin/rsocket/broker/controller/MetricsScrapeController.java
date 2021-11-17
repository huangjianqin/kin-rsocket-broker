package org.kin.rsocket.broker.controller;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import io.rsocket.Payload;
import io.rsocket.util.ByteBufPayload;
import org.kin.rsocket.broker.BrokerResponder;
import org.kin.rsocket.broker.RSocketServiceManager;
import org.kin.rsocket.broker.cluster.BrokerInfo;
import org.kin.rsocket.broker.cluster.RSocketBrokerManager;
import org.kin.rsocket.core.MetricsService;
import org.kin.rsocket.core.RSocketMimeType;
import org.kin.rsocket.core.metadata.GSVRoutingMetadata;
import org.kin.rsocket.core.metadata.MessageAcceptMimeTypesMetadata;
import org.kin.rsocket.core.metadata.MessageMimeTypeMetadata;
import org.kin.rsocket.core.metadata.RSocketCompositeMetadata;
import org.springframework.core.env.Environment;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * metrics scrape controller: scrape metrics from apps
 *
 * @author leijuan
 */
@RestController
@RequestMapping("/metrics")
public class MetricsScrapeController {
    private final ByteBuf metricsScrapeCompositeByteBuf;
    @Resource
    private RSocketServiceManager serviceManager;
    @Resource
    private Environment env;
    @Resource
    private RSocketBrokerManager brokerManager;

    public MetricsScrapeController() {
        /**
         * 初始化请求{@link MetricsService#scrape()}方法的metadata
         */
        RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(
                GSVRoutingMetadata.of(null, MetricsService.class.getCanonicalName(), "scrape", null),
                MessageMimeTypeMetadata.of(RSocketMimeType.JSON),
                MessageAcceptMimeTypesMetadata.of(RSocketMimeType.JSON));
        ByteBuf compositeMetadataContent = compositeMetadata.getContent();
        this.metricsScrapeCompositeByteBuf = Unpooled.copiedBuffer(compositeMetadataContent);
        ReferenceCountUtil.safeRelease(compositeMetadataContent);
    }

    @GetMapping("/prometheus/app/targets")
    public Mono<List<PrometheusAppInstanceConfig>> appTargets() {
        String port = env.getProperty("server.port");
        List<String> hosts = brokerManager.all().stream().map(BrokerInfo::getIp).collect(Collectors.toList());
        int hostSize = hosts.size();
        return Mono.just(serviceManager.getAllResponders().stream()
                .map(responder -> {
                    //随机抽一个broker的/metrics接口作为访问指定app intances的接口
                    String host = hosts.get(responder.getId() % hostSize);
                    return new PrometheusAppInstanceConfig(host, port, "/metrics/" + responder.getUuid());
                })
                .collect(Collectors.toList()));
    }

    @GetMapping("/prometheus/broker/targets")
    public Mono<List<PrometheusAppInstanceConfig>> brokerTargets() {
        String port = env.getProperty("management.server.port");
        return Mono.just(brokerManager.all().stream()
                .map(brokerInfo -> new PrometheusAppInstanceConfig(brokerInfo.getIp(), port, "/actuator/prometheus"))
                .collect(Collectors.toList()));
    }

    @GetMapping(value = "/{uuid}", produces = MimeTypeUtils.TEXT_PLAIN_VALUE)
    public Mono<String> scrape(@PathVariable(name = "uuid") String uuid) {
        BrokerResponder responder = serviceManager.getByUUID(uuid);
        if (responder == null) {
            return Mono.error(new IllegalArgumentException(String.format("app instance not found: %s", uuid)));
        }
        //请求metrics service
        return responder.requestResponse(ByteBufPayload.create(Unpooled.EMPTY_BUFFER, metricsScrapeCompositeByteBuf.retainedDuplicate()))
                .map(Payload::getDataUtf8);
    }

    /**
     * App实例配置
     */
    public static class PrometheusAppInstanceConfig implements Serializable {
        private static final long serialVersionUID = -3127068686701391076L;
        /** 标签 */
        private Map<String, String> labels = new HashMap<>();
        /** 目标实例ip */
        private List<String> targets = new ArrayList<>();

        public PrometheusAppInstanceConfig() {

        }

        public PrometheusAppInstanceConfig(String host, String port, String metricsPath) {
            targets.add(host + ":" + port);
            this.labels.put("__metrics_path__", metricsPath);
        }

        //setter && getter
        public Map<String, String> getLabels() {
            return labels;
        }

        public void setLabels(Map<String, String> labels) {
            this.labels = labels;
        }

        public List<String> getTargets() {
            return targets;
        }

        public void setTargets(List<String> targets) {
            this.targets = targets;
        }
    }
}
