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
import org.kin.rsocket.core.RSocketService;
import org.kin.rsocket.core.RequesterSupport;
import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.ServicesExposedEvent;
import org.kin.rsocket.core.health.HealthChecker;
import org.kin.rsocket.core.metadata.*;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * RSocket Requester Support implementation: setup payload, published service, token and app info
 *
 * @author huangjianqin
 * @date 2021/3/28
 */
@SuppressWarnings({"ConstantConditions", "rawtypes"})
public class DefaultRequesterSupport implements RequesterSupport {
    /** spring config */
    protected final Environment env;
    /** spring rsocket config */
    protected final RSocketServiceProperties config;
    /** app name */
    protected final String appName;
    /** responder acceptor */
    protected final SocketAcceptor socketAcceptor;
    /** spring application context */
    protected ApplicationContext applicationContext;
    /** requester connection responder interceptors */
    protected List<RSocketInterceptor> responderInterceptors = new ArrayList<>();
    /** requester connection requester interceptors */
    protected List<RSocketInterceptor> requesterInterceptors = new ArrayList<>();

    public DefaultRequesterSupport(RSocketServiceProperties config,
                                   Environment env,
                                   ApplicationContext applicationContext,
                                   SocketAcceptor socketAcceptor) {
        this.config = config;
        this.env = env;
        this.applicationContext = applicationContext;
        this.socketAcceptor = socketAcceptor;

        this.appName = env.getProperty("spring.application.name", env.getProperty("application.name"));
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
        //todo 看看需不需要从服务注册中心获取
        return () -> applicationContext.getBeansWithAnnotation(RSocketService.class)
                .values()
                .stream()
                .filter(bean -> !(bean instanceof HealthChecker))
                .map(o -> {
                    Class<?> managedBeanClass = AopUtils.isAopProxy(o) ? AopUtils.getTargetClass(o) : o.getClass();
                    RSocketService rSocketService = AnnotationUtils.findAnnotation(managedBeanClass, RSocketService.class);
                    //noinspection ConstantConditions
                    String serviceName = rSocketService.serviceInterface().getCanonicalName();
                    if (!rSocketService.name().isEmpty()) {
                        serviceName = rSocketService.name();
                    }
                    return new ServiceLocator(
                            config.getGroup(),
                            serviceName,
                            config.getVersion(),
                            rSocketService.tags()
                    );
                }).collect(Collectors.toSet());
    }

    @Override
    public Supplier<Set<ServiceLocator>> subscribedServices() {
        return () -> ServiceReferenceBuilder.CONSUMED_SERVICES;
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

    /**
     * 从{@link Environment}中获取所有配置key
     * todo 逻辑是否需要优化
     */
    private Set<String> getAllConfigKeyNames() {
        Set<String> allNames = new HashSet<>();
        for (PropertySource<?> propertySource : ((AbstractEnvironment) env).getPropertySources()) {
            if (propertySource instanceof EnumerablePropertySource) {
                Collections.addAll(allNames, ((EnumerablePropertySource) propertySource).getPropertyNames());
            }
        }

        Set<String> strNames = new HashSet<>();
        for (Enumeration<?> e = Collections.enumeration(allNames); e.hasMoreElements(); ) {
            Object k = e.nextElement();
            if (k instanceof String) {
                strNames.add((String) k);
            }
        }
        return strNames;
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

    public void addRequesterInterceptor(RSocketInterceptor interceptor) {
        this.requesterInterceptors.add(interceptor);
    }

    public void addResponderInterceptor(RSocketInterceptor interceptor) {
        this.responderInterceptors.add(interceptor);
    }

    public void addRequesterInterceptors(Collection<RSocketInterceptor> interceptors) {
        this.requesterInterceptors.addAll(interceptors);
    }

    public void addResponderInterceptors(Collection<RSocketInterceptor> interceptors) {
        this.responderInterceptors.addAll(interceptors);
    }

    public void addRequesterInterceptors(RSocketInterceptor... interceptors) {
        addRequesterInterceptors(Arrays.asList(interceptors));
    }

    public void addResponderInterceptors(RSocketInterceptor... interceptors) {
        addResponderInterceptors(Arrays.asList(interceptors));
    }
}
