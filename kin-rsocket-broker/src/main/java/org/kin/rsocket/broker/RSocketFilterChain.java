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
public class RSocketFilterChain extends AbstractRSocketFilter {
    /** filter列表 */
    private List<AbstractRSocketFilter> filters = Collections.emptyList();
    /** filter列表Flux */
    private Flux<AbstractRSocketFilter> filterFlux;

    public RSocketFilterChain(List<AbstractRSocketFilter> filters) {
        if (filters != null && !filters.isEmpty()) {
            this.filters = filters;
            this.filterFlux = Flux.fromIterable(filters);
        }
    }

    @Override
    public void updateEnable(boolean enable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Mono<Boolean> shouldFilter(RSocketFilterContext exchange) {
        return Mono.just(true);
    }

    @Override
    public Mono<Void> filter(RSocketFilterContext context) {
        return filterFlux
                .filter(AbstractRSocketFilter::isEnabled)
                .filterWhen(rsocketFilter -> rsocketFilter.shouldFilter(context))
                .concatMap(rsocketFilter -> rsocketFilter.filter(context))
                .then();
    }

    @Override
    public String name() {
        return "rsocket filter chain";
    }

    //setter && getter
    public List<AbstractRSocketFilter> getFilters() {
        return filters == null ? Collections.emptyList() : filters;
    }

    public boolean isFiltersPresent() {
        return CollectionUtils.isNonEmpty(filters);
    }
}
