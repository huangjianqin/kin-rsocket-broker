package org.kin.rsocket.core;

import reactor.core.publisher.Mono;

/**
 * Metrics service for scrape
 *
 * @author huangjianqin
 * @date 2021/8/21
 */
public interface MetricsService {
    Mono<String> scrape();
}
