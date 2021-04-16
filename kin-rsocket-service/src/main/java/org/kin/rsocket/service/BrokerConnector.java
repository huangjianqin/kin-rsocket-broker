package org.kin.rsocket.service;

import io.netty.buffer.Unpooled;
import io.rsocket.Payload;
import io.rsocket.SocketAcceptor;
import io.rsocket.plugins.RSocketInterceptor;
import io.rsocket.util.ByteBufPayload;
import org.kin.framework.Closeable;
import org.kin.framework.utils.NetUtils;
import org.kin.rsocket.core.*;
import org.kin.rsocket.core.event.CloudEventConsumers;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.ServicesExposedEvent;
import org.kin.rsocket.core.event.ServicesHiddenEvent;
import org.kin.rsocket.core.health.HealthCheck;
import org.kin.rsocket.core.metadata.*;
import org.kin.rsocket.core.utils.Symbols;
import org.kin.rsocket.service.event.UpstreamClusterChangedEventConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * broker连接
 *
 * @author huangjianqin
 * @date 2021/3/28
 */
public final class BrokerConnector implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(BrokerConnector.class);
    /** broker uris */
    private final List<String> brokers;
    /** app name */
    private final String appName;
    /** 数据编码格式 */
    private final RSocketMimeType dataMimeType;
    /** upstream cluster manager */
    private final UpstreamClusterManager upstreamClusterManager;
    /** 服务注册中心 */
    private final ReactiveServiceRegistry serviceRegistry;
    /** requester连接配置 */
    private final SimpleRequesterSupport requesterSupport;

    public BrokerConnector(String appName, List<String> brokers,
                           RSocketMimeType dataMimeType, char[] jwtToken) {
        this.appName = appName;
        this.brokers = brokers;
        this.dataMimeType = dataMimeType;
        serviceRegistry = new DefaultServiceRegistry();
        // add health check
        serviceRegistry.addProvider("", HealthCheck.class.getCanonicalName(), "",
                HealthCheck.class, (HealthCheck) serviceName -> Mono.just(1));
        requesterSupport = new SimpleRequesterSupport(jwtToken);

        //init upstream manager
        upstreamClusterManager = new UpstreamClusterManager(requesterSupport);
        upstreamClusterManager.add(null, Symbols.BROKER, null, this.brokers);
        upstreamClusterManager.connect();

        CloudEventConsumers.INSTANCE.addConsumer(new UpstreamClusterChangedEventConsumer(upstreamClusterManager));
    }

    /**
     * 本地注册service
     * todo 支持多种注册方式
     */
    public BrokerConnector registerService(String serviceName, Class<?> serviceInterface, Object provider) {
        this.serviceRegistry.addProvider("", serviceName, "", serviceInterface, provider);
        return this;
    }

    /**
     * 发布(暴露)服务
     */
    public void publishServices() {
        CloudEventData<ServicesExposedEvent> servicesExposedEventCloudEvent = requesterSupport.servicesExposedEvent().get();
        if (servicesExposedEventCloudEvent != null) {
            upstreamClusterManager.getBroker().broadcastCloudEvent(servicesExposedEventCloudEvent).doOnSuccess(aVoid -> {
                String exposedServices = requesterSupport.exposedServices().get().stream().map(ServiceLocator::getGsv).collect(Collectors.joining(","));
                log.info(String.format("Services(%s) published on Brokers(%s)!.", exposedServices, brokers));
            }).subscribe();
        }
    }

    /**
     * 移除服务
     */
    public void removeService(String serviceName, Class<?> serviceInterface) {
        ServiceLocator targetServiceLocator = ServiceLocator.of(serviceName);
        CloudEventData<ServicesHiddenEvent> cloudEvent = ServicesHiddenEvent.of(Collections.singletonList(targetServiceLocator));
        upstreamClusterManager.getBroker().broadcastCloudEvent(cloudEvent)
                .doOnSuccess(unused -> {
                    this.serviceRegistry.removeProvider("", serviceName, "", serviceInterface);
                    log.info(String.format("Services(%s) hide on Brokers(%s)!.", serviceName, brokers));
                }).subscribe();
    }

    /**
     * 构建服务引用
     */
    public <T> T buildServiceReference(Class<T> serviceInterface) {
        return buildServiceReference(serviceInterface, serviceInterface.getCanonicalName());
    }

    /**
     * 构建服务引用
     */
    public <T> T buildServiceReference(Class<T> serviceInterface, String serviceName) {
        return ServiceReferenceBuilder
                .requester(serviceInterface)
                .service(serviceName)
                .encodingType(dataMimeType)
                .acceptEncodingType(dataMimeType)
                .upstreamClusterManager(upstreamClusterManager)
                .build();
    }

    /**
     * dispose
     */
    public void dispose() {
        upstreamClusterManager.close();
        RSocketAppContext.CLOUD_EVENT_SINK.tryEmitComplete();
    }

    @Override
    public void close() {
        dispose();
    }

    //------------------------------------------------------------------------------------------------------------------------------------
    private class SimpleRequesterSupport implements RequesterSupport {
        /** 加密 */
        private final char[] jwtToken;

        public SimpleRequesterSupport(char[] jwtToken) {
            this.jwtToken = jwtToken;
        }

        @Override
        public URI originUri() {
            return URI.create("tcp://" + NetUtils.getIp() + "?appName=" + appName + "&uuid=" + RSocketAppContext.ID);
        }

        @Override
        public Supplier<Payload> setupPayload() {
            return () -> {
                //composite metadata with app metadata
                List<MetadataAware> metadataAwares = new ArrayList<>(2);
                metadataAwares.add(getAppMetadata());
                if (jwtToken != null && jwtToken.length > 0) {
                    metadataAwares.add(BearerTokenMetadata.jwt(jwtToken));
                }
                Set<ServiceLocator> serviceLocators = exposedServices().get();
                if (!serviceLocators.isEmpty()) {
                    ServiceRegistryMetadata serviceRegistryMetadata = new ServiceRegistryMetadata();
                    serviceRegistryMetadata.setPublished(serviceLocators);
                    metadataAwares.add(serviceRegistryMetadata);
                }
                RSocketCompositeMetadata compositeMetadata = RSocketCompositeMetadata.of(metadataAwares);
                return ByteBufPayload.create(Unpooled.EMPTY_BUFFER, compositeMetadata.getContent());
            };
        }

        @Override
        public Supplier<Set<ServiceLocator>> exposedServices() {
            Set<String> allServices = serviceRegistry.findAllServices();
            if (!allServices.isEmpty()) {
                return () -> allServices.stream()
                        //过滤掉local service
                        .filter(serviceName -> !serviceName.equals(HealthCheck.class.getCanonicalName())
                                && !serviceName.equals(ReactiveServiceRegistry.class.getCanonicalName()))
                        //todo
                        .map(ServiceLocator::of)
                        .collect(Collectors.toSet());
            }
            return Collections::emptySet;
        }

        @Override
        public Supplier<Set<ServiceLocator>> subscribedServices() {
            //todo
            return Collections::emptySet;
        }

        @Override
        public SocketAcceptor socketAcceptor() {
            return (setupPayload, requester) -> Mono.fromCallable(() -> new Responder(serviceRegistry, requester, setupPayload));
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

        /**
         * 获取app数据
         */
        private AppMetadata getAppMetadata() {
            //app metadata
            AppMetadata appMetadata = new AppMetadata();
            appMetadata.setUuid(RSocketAppContext.ID);
            appMetadata.setName(appName);
            appMetadata.setIp(NetUtils.getIp());
            appMetadata.setDevice(appName);
            appMetadata.setBrokers(brokers);
            appMetadata.setRsocketPorts(RSocketAppContext.rsocketPorts);
            //web port
            appMetadata.setWebPort(RSocketAppContext.webPort);
            //management port
            appMetadata.setManagementPort(RSocketAppContext.managementPort);
            appMetadata.setSecure(Objects.nonNull(jwtToken) && jwtToken.length > 0);
            return appMetadata;
        }
    }

    public static class Builder {

    }
}
