package org.kin.rsocket.broker;

import io.netty.buffer.Unpooled;
import io.rsocket.Payload;
import io.rsocket.SocketAcceptor;
import io.rsocket.plugins.RSocketInterceptor;
import io.rsocket.util.ByteBufPayload;
import org.kin.framework.utils.NetUtils;
import org.kin.rsocket.core.RSocketAppContext;
import org.kin.rsocket.core.RSocketFilterChain;
import org.kin.rsocket.core.RequesterSupport;
import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.broker.ServicesExposedEvent;
import org.kin.rsocket.core.metadata.AppMetadata;
import org.kin.rsocket.core.metadata.BearerTokenMetadata;
import org.kin.rsocket.core.metadata.MetadataAware;
import org.kin.rsocket.core.metadata.RSocketCompositeMetadata;
import org.springframework.core.env.Environment;
import reactor.core.publisher.Mono;
import reactor.extra.processor.TopicProcessor;

import java.net.URI;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author huangjianqin
 * @date 2021/3/30
 */
public class SubBrokerRequester implements RequesterSupport {
    /** broker config */
    private final RSocketBrokerProperties brokerConfig;
    /** spring env */
    private final Environment env;
    /** app name */
    private final String appName;
    /** jwt认证 */
    private final char[] jwtToken;
    /** 服务实例路由表 */
    private final ServiceRouteTable routeTable;
    /** 服务路由器 */
    private final ServiceRouter serviceRouter;
    /** rsocket filter chain */
    private final RSocketFilterChain filterChain;
    /** reactive event processor */
    private final TopicProcessor<CloudEventData<?>> eventProcessor;

    public SubBrokerRequester(RSocketBrokerProperties brokerConfig,
                              Environment env,
                              ServiceRouteTable routeTable,
                              ServiceRouter serviceRouter,
                              RSocketFilterChain filterChain,
                              TopicProcessor<CloudEventData<?>> eventProcessor) {
        this.env = env;
        //todo 配置同一
        this.appName = env.getProperty("spring.application.name", env.getProperty("application.name", "unknown-app"));
        this.jwtToken = env.getProperty("rsocket.jwt-token", "").toCharArray();
        this.routeTable = routeTable;
        this.serviceRouter = serviceRouter;
        this.filterChain = filterChain;
        this.brokerConfig = brokerConfig;
        this.eventProcessor = eventProcessor;
    }

    @Override
    public URI originUri() {
        return URI.create("tcp://" + NetUtils.getIp() + ":" + brokerConfig.getPort()
                + "?appName=" + appName
                + "&uuid=" + RSocketAppContext.ID);
    }

    @Override
    public Supplier<Payload> setupPayload() {
        return () -> {
            List<MetadataAware> metadataAwares = new ArrayList<>(2);
            metadataAwares.add(getAppMetadata());

            // authentication
            if (this.jwtToken != null && this.jwtToken.length > 0) {
                metadataAwares.add(BearerTokenMetadata.jwt(this.jwtToken));
            }

            //composite metadata with app metadata
            RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(metadataAwares);
            return ByteBufPayload.create(Unpooled.EMPTY_BUFFER, compositeMetadata.getContent());
        };
    }

    @Override
    public Supplier<Set<ServiceLocator>> exposedServices() {
        return () -> routeTable.getAllServices().stream()
                //todo global是啥子
                .filter(serviceLocator -> serviceLocator.hasTag("global"))
                .collect(Collectors.toSet());
    }

    @Override
    public Supplier<Set<ServiceLocator>> subscribedServices() {
        //todo
        return Collections::emptySet;
    }

    @Override
    public Supplier<CloudEventData<ServicesExposedEvent>> servicesExposedEvent() {
        return () -> {
            Collection<ServiceLocator> serviceLocators = exposedServices().get();
            if (serviceLocators.isEmpty()) {
                return null;
            }

            return ServicesExposedEvent.of(serviceLocators);
        };
    }

    @Override
    public SocketAcceptor socketAcceptor() {
        return (connectionSetupPayload, rsocket) -> Mono.just(
                new BrokerResponder(routeTable, serviceRouter, filterChain, eventProcessor));
    }

    @Override
    public List<RSocketInterceptor> responderInterceptors() {
        return Collections.emptyList();
    }

    @Override
    public List<RSocketInterceptor> requesterInterceptors() {
        return Collections.emptyList();
    }

    private AppMetadata getAppMetadata() {
        System.out.println("app metadata");
        //app metadata
        AppMetadata appMetadata = new AppMetadata();
        appMetadata.setUuid(RSocketAppContext.ID);
        appMetadata.setName(appName);
        appMetadata.setIp(NetUtils.getIp());
        appMetadata.setDevice("SpringBootApp");
        appMetadata.setRsocketPorts(RSocketAppContext.rsocketPorts);
        //brokers
        appMetadata.setBrokers(brokerConfig.getUpstreamBrokers());
        appMetadata.setTopology(brokerConfig.getTopology());
        appMetadata.setRsocketPorts(RSocketAppContext.rsocketPorts);
        //web port
        appMetadata.setWebPort(Integer.parseInt(env.getProperty("server.port", "0")));
        appMetadata.setManagementPort(appMetadata.getWebPort());
        //management port
        if (env.getProperty("management.server.port") != null) {
            appMetadata.setManagementPort(Integer.parseInt(env.getProperty("management.server.port", "0")));
        }
        appMetadata.setManagementPort(Integer.parseInt(env.getProperty("management.server.port", "" + appMetadata.getWebPort())));
        appMetadata.addMetadata("broker", "true");
        return appMetadata;
    }
}