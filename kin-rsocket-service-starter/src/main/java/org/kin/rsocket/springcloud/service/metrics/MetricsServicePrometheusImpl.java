package org.kin.rsocket.springcloud.service.metrics;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.kin.rsocket.core.MetricsService;
import org.kin.rsocket.core.RSocketService;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;

/**
 * metrics rsocket service Prometheus implementation
 *
 * @author huangjianqin
 * @date 2021/8/21
 */
@RSocketService(MetricsService.class)
public class MetricsServicePrometheusImpl implements MetricsService {
    @Autowired
    private PrometheusMeterRegistry meterRegistry;

    @Override
    public Mono<String> scrape() {
        return Mono.fromCallable(() -> meterRegistry.scrape());
    }
}
