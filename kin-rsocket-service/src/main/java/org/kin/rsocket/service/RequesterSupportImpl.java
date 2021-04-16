package org.kin.rsocket.service;

import io.netty.buffer.Unpooled;
import io.rsocket.Payload;
import io.rsocket.SocketAcceptor;
import io.rsocket.plugins.RSocketInterceptor;
import io.rsocket.util.ByteBufPayload;
import org.kin.framework.collection.Tuple;
import org.kin.framework.utils.NetUtils;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.core.RSocketAppContext;
import org.kin.rsocket.core.ReactiveServiceRegistry;
import org.kin.rsocket.core.RequesterSupport;
import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.health.HealthCheck;
import org.kin.rsocket.core.metadata.*;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * service端requester配置
 *
 * @author huangjianqin
 * @date 2021/3/28
 */
@SuppressWarnings({"ConstantConditions", "rawtypes"})
final class RequesterSupportImpl implements RequesterSupport {
    /** spring rsocket config */
    private final RSocketServiceProperties config;
    /** app name */
    private final String appName;
    /** responder acceptor */
    private final SocketAcceptor socketAcceptor;
    /** 服务注册中心 */
    private final ReactiveServiceRegistry serviceRegistry;
    /** requester connection responder interceptors */
    private List<RSocketInterceptor> responderInterceptors = new ArrayList<>();
    /** requester connection requester interceptors */
    private List<RSocketInterceptor> requesterInterceptors = new ArrayList<>();

    public RequesterSupportImpl(RSocketServiceProperties config,
                                String appName,
                                ReactiveServiceRegistry serviceRegistry,
                                SocketAcceptor socketAcceptor) {
        this.config = config;
        this.serviceRegistry = serviceRegistry;
        this.socketAcceptor = socketAcceptor;

        this.appName = appName;
    }

    @Override
    public URI originUri() {
        return URI.create(config.getSchema() + "://" + NetUtils.getIp() + ":" + config.getPort()
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
            Set<ServiceLocator> serviceLocators = exposedServices().get();
            if (!serviceLocators.isEmpty()) {
                ServiceRegistryMetadata serviceRegistryMetadata = new ServiceRegistryMetadata();
                serviceRegistryMetadata.setPublished(serviceLocators);
                metadataAwares.add(serviceRegistryMetadata);
            }
            // authentication
            if (StringUtils.isNotBlank(config.getJwtToken())) {
                metadataAwares.add(BearerTokenMetadata.jwt(config.getJwtToken().toCharArray()));
            }
            RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(metadataAwares);
            return ByteBufPayload.create(Unpooled.EMPTY_BUFFER, compositeMetadata.getContent());
        };
    }

    @Override
    public Supplier<Set<ServiceLocator>> exposedServices() {
        return () -> serviceRegistry.findAllServiceLocators()
                .stream()
                //过滤掉local service
                .filter(l -> !l.getService().equals(HealthCheck.class.getCanonicalName())
                        && !l.getService().equals(ReactiveServiceRegistry.class.getCanonicalName()))
                .collect(Collectors.toSet());
    }

    @Override
    public Supplier<Set<ServiceLocator>> subscribedServices() {
        return () -> ServiceReferenceBuilder.CONSUMED_SERVICES;
    }

    /** 获取app元数据 */
    private AppMetadata getAppMetadata() {
        //app metadata
        AppMetadata appMetadata = new AppMetadata();
        appMetadata.setUuid(RSocketAppContext.ID);
        appMetadata.setName(appName);
        appMetadata.setIp(NetUtils.getIp());
        appMetadata.setDevice("SpringBootApp");
        //brokers
        appMetadata.setBrokers(config.getBrokers());
        appMetadata.setTopology(config.getTopology());
        appMetadata.setRsocketPorts(RSocketAppContext.rsocketPorts);
        //web port
        appMetadata.setWebPort(RSocketAppContext.webPort);
        //management port
        appMetadata.setManagementPort(RSocketAppContext.managementPort);
        //元数据
        appMetadata.setMetadata(
                config.getMetadata().entrySet().stream()
                        .map(e -> new Tuple<>(
                                e.getKey().split("[=:]", 2)[0].trim().replace("kin.rsocket.metadata.", ""),
                                e.getValue()))
                        .collect(Collectors.toMap(Tuple::first, Tuple::second)));
        //todo
        //power unit
        if (appMetadata.getMetadata("power-rating") != null) {
            appMetadata.setPowerRating(Integer.parseInt(appMetadata.getMetadata("power-rating")));
        }
        appMetadata.setSecure(StringUtils.isNotBlank(config.getJwtToken()));
        //todo 有没有必要将整个文件内容读进内存
        //humans.md
        URL humansMd = this.getClass().getResource("/humans.md");
        if (humansMd != null) {
            try (InputStream inputStream = humansMd.openStream()) {
                byte[] bytes = new byte[inputStream.available()];
                inputStream.read(bytes);
                inputStream.close();
                appMetadata.setHumansMd(new String(bytes, StandardCharsets.UTF_8));
            } catch (Exception ignore) {

            }
        }
        return appMetadata;
    }

    @Override
    public SocketAcceptor socketAcceptor() {
        return this.socketAcceptor;
    }

    @Override
    public List<RSocketInterceptor> responderInterceptors() {
        return this.responderInterceptors;
    }

    @Override
    public List<RSocketInterceptor> requesterInterceptors() {
        return requesterInterceptors;
    }

    void addRequesterInterceptor(RSocketInterceptor interceptor) {
        this.requesterInterceptors.add(interceptor);
    }

    void addResponderInterceptor(RSocketInterceptor interceptor) {
        this.responderInterceptors.add(interceptor);
    }

    void addRequesterInterceptors(Collection<RSocketInterceptor> interceptors) {
        this.requesterInterceptors.addAll(interceptors);
    }

    void addResponderInterceptors(Collection<RSocketInterceptor> interceptors) {
        this.responderInterceptors.addAll(interceptors);
    }

    void addRequesterInterceptors(RSocketInterceptor... interceptors) {
        addRequesterInterceptors(Arrays.asList(interceptors));
    }

    void addResponderInterceptors(RSocketInterceptor... interceptors) {
        addResponderInterceptors(Arrays.asList(interceptors));
    }
}
