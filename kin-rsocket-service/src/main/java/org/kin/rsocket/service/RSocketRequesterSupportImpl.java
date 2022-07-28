package org.kin.rsocket.service;

import brave.Tracer;
import io.netty.buffer.Unpooled;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.SocketAcceptor;
import io.rsocket.plugins.DuplexConnectionInterceptor;
import io.rsocket.plugins.RSocketInterceptor;
import io.rsocket.plugins.RequestInterceptor;
import io.rsocket.util.ByteBufPayload;
import org.kin.framework.collection.Tuple;
import org.kin.framework.utils.NetUtils;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.core.LocalRSocketServiceRegistry;
import org.kin.rsocket.core.RSocketAppContext;
import org.kin.rsocket.core.RSocketRequesterSupport;
import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.metadata.*;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * service端requester配置
 *
 * @author huangjianqin
 * @date 2021/3/28
 */
public final class RSocketRequesterSupportImpl implements RSocketRequesterSupport {
    /** rsocket config */
    private final RSocketServiceProperties rsocketServiceProperties;
    /** app name */
    private final String appName;
    /** @see io.rsocket.plugins.InterceptorRegistry#forRequester(RSocketInterceptor) */
    private final List<RSocketInterceptor> requesterInterceptors = new ArrayList<>();
    /** @see io.rsocket.plugins.InterceptorRegistry#forResponder(RSocketInterceptor) */
    private final List<RSocketInterceptor> responderInterceptors = new ArrayList<>();
    /** @see io.rsocket.plugins.InterceptorRegistry#forConnection(Consumer) */
    private final List<DuplexConnectionInterceptor> connectionInterceptors = new ArrayList<>();
    /** @see io.rsocket.plugins.InterceptorRegistry#forRequestsInRequester(Function) */
    private final List<Function<RSocket, ? extends RequestInterceptor>> requesterRequestInterceptors = new ArrayList<>();
    /** @see io.rsocket.plugins.InterceptorRegistry#forRequestsInResponder(Function) */
    private final List<Function<RSocket, ? extends RequestInterceptor>> responderRequestInterceptors = new ArrayList<>();
    /** 用于获取开启p2p服务gsv */
    private UpstreamClusterManager upstreamClusterManager;
    /** zipkin */
    private final Tracer tracer;

    public RSocketRequesterSupportImpl(RSocketServiceProperties rsocketServiceProperties, String appName) {
        this(rsocketServiceProperties, appName, null);
    }

    public RSocketRequesterSupportImpl(RSocketServiceProperties rsocketServiceProperties, String appName, Tracer tracer) {
        this.rsocketServiceProperties = rsocketServiceProperties;
        this.appName = appName;
        this.tracer = tracer;
    }

    @Override
    public URI originUri() {
        return URI.create(rsocketServiceProperties.getSchema() + "://" + NetUtils.getIp() + ":" + rsocketServiceProperties.getPort()
                + "?appName=" + appName
                + "&uuid=" + RSocketAppContext.ID);
    }

    @Override
    public Supplier<Payload> setupPayload() {
        return () -> {
            List<MetadataAware> metadataAwares = new ArrayList<>(3);
            //app metadata
            metadataAwares.add(getAppMetadata());
            //published services
            Set<ServiceLocator> serviceLocators = LocalRSocketServiceRegistry.exposedServices();
            if (!serviceLocators.isEmpty()) {
                RSocketServiceRegistryMetadata.Builder builder = RSocketServiceRegistryMetadata.builder();
                builder.addPublishedServices(serviceLocators);
                metadataAwares.add(builder.build());
            }
            // authentication
            if (StringUtils.isNotBlank(rsocketServiceProperties.getJwtToken())) {
                metadataAwares.add(BearerTokenMetadata.jwt(rsocketServiceProperties.getJwtToken().toCharArray()));
            }
            RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.from(metadataAwares);
            return ByteBufPayload.create(Unpooled.EMPTY_BUFFER, compositeMetadata.getContent());
        };
    }

    /** 获取app元数据 */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private AppMetadata getAppMetadata() {
        //app metadata
        AppMetadata.Builder builder = AppMetadata.builder();
        builder.uuid(RSocketAppContext.ID);
        builder.name(appName);
        builder.ip(NetUtils.getIp());
        builder.device("JvmApp");
        //brokers
        builder.brokers(rsocketServiceProperties.getBrokers());
        if (Objects.nonNull(upstreamClusterManager)) {
            builder.p2pServices(upstreamClusterManager.getP2pServices());
        }
        builder.topology(rsocketServiceProperties.getTopology());
        builder.rsocketPorts(RSocketAppContext.rsocketPorts);
        //web port
        builder.webPort(RSocketAppContext.webPort);
        //management port
        builder.managementPort(RSocketAppContext.managementPort);
        //元数据
        Map<String, String> metadata = rsocketServiceProperties.getMetadata().entrySet().stream()
                .map(e -> new Tuple<>(
                        e.getKey().split("[=:]", 2)[0].trim().replace("kin.rsocket.metadata.", ""),
                        e.getValue()))
                .collect(Collectors.toMap(Tuple::first, Tuple::second));
        builder.metadata(metadata);
        //weight
        if (metadata.containsKey(RSocketServiceMetadataKeys.WEIGHT)) {
            builder.weight(Integer.parseInt(metadata.get(RSocketServiceMetadataKeys.WEIGHT)));
        }
        builder.secure(StringUtils.isNotBlank(rsocketServiceProperties.getJwtToken()));

        //humans.md
        URL humansMd = this.getClass().getResource("/humans.md");
        if (humansMd != null) {
            try (InputStream inputStream = humansMd.openStream()) {
                byte[] bytes = new byte[inputStream.available()];
                inputStream.read(bytes);
                inputStream.close();
                builder.humansMd(new String(bytes, StandardCharsets.UTF_8));
            } catch (Exception ignore) {
                //do nothing
            }
        }
        return builder.build();
    }

    @Override
    public SocketAcceptor socketAcceptor() {
        return (setupPayload, requester) -> Mono.just(new RSocketBrokerOrServiceRequestHandler(requester, setupPayload, tracer));
    }

    @Override
    public List<RSocketInterceptor> responderInterceptors() {
        return Collections.unmodifiableList(responderInterceptors);
    }

    @Override
    public List<RSocketInterceptor> requesterInterceptors() {
        return Collections.unmodifiableList(requesterInterceptors);
    }

    @Override
    public List<DuplexConnectionInterceptor> connectionInterceptors() {
        return connectionInterceptors;
    }

    @Override
    public List<Function<RSocket, ? extends RequestInterceptor>> requesterRequestInterceptors() {
        return requesterRequestInterceptors;
    }

    @Override
    public List<Function<RSocket, ? extends RequestInterceptor>> responderRequestInterceptors() {
        return responderRequestInterceptors;
    }

    public void addRequesterInterceptors(Collection<RSocketInterceptor> interceptors) {
        this.requesterInterceptors.addAll(interceptors);
    }

    public void addRequesterInterceptors(RSocketInterceptor... interceptors) {
        addRequesterInterceptors(Arrays.asList(interceptors));
    }

    public void addResponderInterceptors(Collection<RSocketInterceptor> interceptors) {
        this.responderInterceptors.addAll(interceptors);
    }

    public void addResponderInterceptors(RSocketInterceptor... interceptors) {
        addResponderInterceptors(Arrays.asList(interceptors));
    }

    public void addConnectionInterceptors(Collection<DuplexConnectionInterceptor> interceptors) {
        this.connectionInterceptors.addAll(interceptors);
    }

    public void addConnectionInterceptors(DuplexConnectionInterceptor... interceptors) {
        addConnectionInterceptors(Arrays.asList(interceptors));
    }

    public void addResponderRequestInterceptors(Collection<Function<RSocket, ? extends RequestInterceptor>> interceptors) {
        this.responderRequestInterceptors.addAll(interceptors);
    }

    public void addResponderRequestInterceptors(Function<RSocket, ? extends RequestInterceptor>... interceptors) {
        addResponderRequestInterceptors(Arrays.asList(interceptors));
    }

    public void addRequesterRequestInterceptors(Collection<Function<RSocket, ? extends RequestInterceptor>> interceptors) {
        this.requesterRequestInterceptors.addAll(interceptors);
    }

    public void addRequesterRequestInterceptors(Function<RSocket, ? extends RequestInterceptor>... interceptors) {
        addRequesterRequestInterceptors(Arrays.asList(interceptors));
    }

    //setter && getter
    void setUpstreamClusterManager(UpstreamClusterManager upstreamClusterManager) {
        this.upstreamClusterManager = upstreamClusterManager;
    }
}
