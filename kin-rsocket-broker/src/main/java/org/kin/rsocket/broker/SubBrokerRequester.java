package org.kin.rsocket.broker;

import io.netty.buffer.Unpooled;
import io.rsocket.Payload;
import io.rsocket.SocketAcceptor;
import io.rsocket.plugins.RSocketInterceptor;
import io.rsocket.util.ByteBufPayload;
import org.kin.framework.utils.NetUtils;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.core.RSocketAppContext;
import org.kin.rsocket.core.RequesterSupport;
import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.ServicesExposedEvent;
import org.kin.rsocket.core.metadata.AppMetadata;
import org.kin.rsocket.core.metadata.BearerTokenMetadata;
import org.kin.rsocket.core.metadata.MetadataAware;
import org.kin.rsocket.core.metadata.RSocketCompositeMetadata;
import org.springframework.core.env.Environment;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.net.URI;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author huangjianqin
 * @date 2021/3/30
 */
final class SubBrokerRequester implements RequesterSupport {
    /** broker config */
    private final RSocketBrokerProperties brokerConfig;
    /** spring env */
    private final Environment env;
    /** app name */
    private final String appName;
    /** 服务路由器 */
    private final ServiceManager serviceManager;
    /** rsocket filter chain */
    private final RSocketFilterChain filterChain;
    /** reactive cloud event flux */
    private final Sinks.Many<CloudEventData<?>> cloudEventSink;

    public SubBrokerRequester(RSocketBrokerProperties brokerConfig,
                              Environment env,
                              ServiceManager serviceManager,
                              RSocketFilterChain filterChain,
                              Sinks.Many<CloudEventData<?>> cloudEventSink) {
        this.env = env;
        //todo 配置同一
        this.appName = env.getProperty("spring.application.name", env.getProperty("application.name", "unknown-app"));
        this.serviceManager = serviceManager;
        this.filterChain = filterChain;
        this.brokerConfig = brokerConfig;
        this.cloudEventSink = cloudEventSink;
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
            if (StringUtils.isNotBlank(brokerConfig.getUpstreamToken())) {
                metadataAwares.add(BearerTokenMetadata.jwt(brokerConfig.getUpstreamToken().toCharArray()));
            }

            //composite metadata with app metadata
            RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(metadataAwares);
            return ByteBufPayload.create(Unpooled.EMPTY_BUFFER, compositeMetadata.getContent());
        };
    }

    @Override
    public Supplier<Set<ServiceLocator>> exposedServices() {
        return () -> serviceManager.getAllServices().stream()
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
                new BrokerResponder(serviceManager, filterChain, cloudEventSink));
    }

    @Override
    public List<RSocketInterceptor> responderInterceptors() {
        //todo
        return Collections.emptyList();
    }

    @Override
    public List<RSocketInterceptor> requesterInterceptors() {
        //todo
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
        //upstream brokers
        appMetadata.setBrokers(brokerConfig.getUpstreamBrokers());
        appMetadata.setRsocketPorts(RSocketAppContext.rsocketPorts);
        //web port
        appMetadata.setWebPort(RSocketAppContext.webPort);
        //management port
        appMetadata.setManagementPort(RSocketAppContext.managementPort);
        RSocketBrokerProperties.RSocketSSL socketSSL = brokerConfig.getSsl();
        appMetadata.setSecure(!Objects.isNull(socketSSL) && socketSSL.isEnabled());

        //todo  这个元数据key要不要定义常量
        appMetadata.addMetadata("broker", "true");
        return appMetadata;
    }
}