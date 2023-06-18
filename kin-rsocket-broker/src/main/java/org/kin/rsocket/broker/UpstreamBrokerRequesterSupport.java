package org.kin.rsocket.broker;

import io.netty.buffer.Unpooled;
import io.rsocket.Payload;
import io.rsocket.SocketAcceptor;
import io.rsocket.util.ByteBufPayload;
import org.kin.framework.utils.NetUtils;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.core.RSocketAppContext;
import org.kin.rsocket.core.RSocketRequesterSupport;
import org.kin.rsocket.core.metadata.AppMetadata;
import org.kin.rsocket.core.metadata.BearerTokenMetadata;
import org.kin.rsocket.core.metadata.MetadataAware;
import org.kin.rsocket.core.metadata.RSocketCompositeMetadata;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * @author huangjianqin
 * @date 2021/3/30
 */
final class UpstreamBrokerRequesterSupport implements RSocketRequesterSupport {
    /** broker config */
    private final RSocketBrokerProperties brokerConfig;
    /** app name */
    private final String appName;
    /** 服务路由器 */
    private final RSocketServiceRegistry serviceRegistry;
    /** rsocket filter chain */
    private final RSocketFilterChain filterChain;

    public UpstreamBrokerRequesterSupport(RSocketBrokerProperties brokerConfig,
                                          String appName,
                                          RSocketServiceRegistry serviceRegistry,
                                          RSocketFilterChain filterChain) {
        this.appName = appName;
        this.serviceRegistry = serviceRegistry;
        this.filterChain = filterChain;
        this.brokerConfig = brokerConfig;
    }

    @Override
    public URI originUri() {
        return URI.create("tcp://" + NetUtils.getLocalAddressIp() + ":" + brokerConfig.getPort()
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
            RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.from(metadataAwares);
            return ByteBufPayload.create(Unpooled.EMPTY_BUFFER, compositeMetadata.getContent());
        };
    }

    @Override
    public SocketAcceptor socketAcceptor() {
        return (setupPayload, rsocket) -> Mono.just(new RSocketBrokerRequestHandler(serviceRegistry, filterChain, setupPayload));
    }

    private AppMetadata getAppMetadata() {
        //app metadata
        AppMetadata.Builder builder = AppMetadata.builder();
        builder.uuid(RSocketAppContext.ID);
        builder.name(appName);
        builder.ip(NetUtils.getLocalAddressIp());
        builder.device("KinRSocketBroker");
        //upstream brokers
        builder.brokers(brokerConfig.getUpstreamBrokers());
        builder.rsocketPorts(RSocketAppContext.rsocketPorts);
        //web port
        builder.webPort(RSocketAppContext.webPort);
        //management port
        builder.managementPort(RSocketAppContext.managementPort);
        RSocketBrokerProperties.RSocketSSL rsocketSSL = brokerConfig.getSsl();
        builder.secure(!Objects.isNull(rsocketSSL) && rsocketSSL.isEnabled());

        //标识app是broker
        builder.addMetadata(BrokerMetadataKeys.BROKER, "true");
        return builder.build();
    }
}