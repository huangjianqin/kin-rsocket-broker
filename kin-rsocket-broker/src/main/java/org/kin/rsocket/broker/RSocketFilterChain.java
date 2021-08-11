package org.kin.rsocket.broker;

import org.kin.framework.utils.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

/**
 * @author huangjianqin
 * @date 2021/3/27
 */
final class RSocketFilterChain {
    /** filter列表 */
    private List<AbstractRSocketFilter> filters = Collections.emptyList();
    /** filter列表Flux */
    private Flux<AbstractRSocketFilter> filterFlux;

    public RSocketFilterChain(List<AbstractRSocketFilter> filters) {
        if (filters != null && !filters.isEmpty()) {
            this.filters = Collections.unmodifiableList(filters);
            this.filterFlux = Flux.fromIterable(filters);
        }
    }

    /**
     * filter逻辑
     */
    public Mono<Void> filter(RSocketFilterContext context) {
        return filterFlux
                .filter(AbstractRSocketFilter::isEnabled)
                .filterWhen(rsocketFilter -> rsocketFilter.shouldFilter(context))
                .concatMap(rsocketFilter -> rsocketFilter.filter(context))
                .then();
    }

    //setter && getter
    public List<AbstractRSocketFilter> getFilters() {
        return filters == null ? Collections.emptyList() : filters;
    }

    public boolean isFiltersPresent() {
        return CollectionUtils.isNonEmpty(filters);
    }
}
