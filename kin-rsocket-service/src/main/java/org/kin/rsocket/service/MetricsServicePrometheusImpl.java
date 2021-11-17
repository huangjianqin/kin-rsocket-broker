package org.kin.rsocket.service;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.kin.rsocket.core.MetricsService;
import org.kin.rsocket.core.RSocketService;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;

/**
 * metrics rsocket service Prometheus implementation
 *
 * @author huangjianqin
 * @date 2021/8/21
 */
@RSocketService(MetricsService.class)
public class MetricsServicePrometheusImpl implements MetricsService {
    @Resource
    private PrometheusMeterRegistry meterRegistry;

    @Override
    public Mono<String> scrape() {
        return Mono.fromCallable(() -> meterRegistry.scrape());
    }
}
