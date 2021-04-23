package org.kin.rsocket.service;

import com.google.common.base.Preconditions;
import org.kin.framework.collection.ConcurrentHashSet;
import org.kin.framework.utils.CollectionUtils;
import org.kin.rsocket.core.*;
import org.kin.rsocket.service.utils.ByteBuddyUtils;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;

/**
 * @author huangjianqin
 * @date 2021/3/27
 */
public final class ServiceReferenceBuilder<T> {
    /** 缓存的所有requester proxy, 用于监控 */
    public static final Set<ServiceLocator> CONSUMED_SERVICES = new ConcurrentHashSet<>();

    /** uri */
    private URI sourceUri;
    /** group */
    private String group;
    /** service */
    private String service;
    /** version */
    private String version;
    /** call timeout, 默认3s */
    private Duration callTimeout = Duration.ofMillis(3000);
    /**
     * endpoint
     * 形式:
     * 1. id:XX
     * 2. uuid:XX
     * 3. ip:XX
     */
    private String endpoint;
    /**
     * sticky session
     * 相当于固定session, 指定service首次请求后, 后面请求都是route到该service instance
     * 如果该service instance失效, 重新选择一个sticky service instance
     * 目前仅仅在service mesh校验通过下才允许mark sticky service instance
     */
    private boolean sticky;
    /** 服务接口 */
    private Class<T> serviceInterface;
    /** 数据编码类型 */
    private RSocketMimeType encodingType = RSocketMimeType.Java_Object;
    /** accept 编码类型 */
    private RSocketMimeType[] acceptEncodingTypes = new RSocketMimeType[]{RSocketMimeType.Java_Object};
    /** 对应的upstream cluster */
    private UpstreamCluster upstreamCluster;

    private ServiceReferenceBuilder() {
    }

    public static <T> ServiceReferenceBuilder<T> requester(Class<T> serviceInterface) {
        ServiceReferenceBuilder<T> builder = new ServiceReferenceBuilder<T>();
        builder.serviceInterface = serviceInterface;
        builder.service = serviceInterface.getCanonicalName();
        //解析interface class 上的@ServiceMapping注解
        ServiceMapping serviceMapping = serviceInterface.getAnnotation(ServiceMapping.class);
        if (serviceMapping != null) {
            if (!serviceMapping.group().isEmpty()) {
                builder.group = serviceMapping.group();
            }
            if (!serviceMapping.version().isEmpty()) {
                builder.version = serviceMapping.group();
            }
            if (!serviceMapping.value().isEmpty()) {
                builder.service = serviceMapping.value();
            }
            if (!serviceMapping.endpoint().isEmpty()) {
                builder.endpoint = serviceMapping.endpoint();
            }
            if (!serviceMapping.paramEncoding().isEmpty()) {
                builder.encodingType = RSocketMimeType.getByType(serviceMapping.paramEncoding());
            }
            if (CollectionUtils.isNonEmpty(serviceMapping.resultEncoding())) {
                builder.acceptEncodingTypes = Arrays.stream(serviceMapping.resultEncoding()).map(RSocketMimeType::getByType).toArray(RSocketMimeType[]::new);
            }
            builder.sticky = serviceMapping.sticky();
        }
        return builder;
    }

    public ServiceReferenceBuilder<T> group(String group) {
        this.group = group;
        return this;
    }

    public ServiceReferenceBuilder<T> service(String service) {
        this.service = service;
        return this;
    }

    public ServiceReferenceBuilder<T> version(String version) {
        this.version = version;
        return this;
    }

    public ServiceReferenceBuilder<T> callTimeout(int millis) {
        this.callTimeout = Duration.ofMillis(millis);
        return this;
    }

    public ServiceReferenceBuilder<T> endpoint(String endpoint) {
        Preconditions.checkArgument(endpoint.contains(":"));
        this.endpoint = endpoint;
        return this;
    }

    public ServiceReferenceBuilder<T> sticky(boolean sticky) {
        this.sticky = sticky;
        return this;
    }

    /**
     * 一般使用{@link #upstreamClusterManager(UpstreamClusterManager)}, 因为其会自动寻找对应serviceId的UpstreamCluster
     */
    public ServiceReferenceBuilder<T> upstream(UpstreamCluster upstreamCluster) {
        this.upstreamCluster = upstreamCluster;
        return this;
    }

    public ServiceReferenceBuilder<T> encodingType(RSocketMimeType encodingType) {
        this.encodingType = encodingType;
        return this;
    }

    public ServiceReferenceBuilder<T> acceptEncodingTypes(RSocketMimeType... mimeTypes) {
        this.acceptEncodingTypes = mimeTypes;
        return this;
    }

    /**
     * GraalVM nativeImage support: set encodeType and acceptEncodingType to Json
     */
    public ServiceReferenceBuilder<T> nativeImage() {
        encodingType(RSocketMimeType.Json);
        acceptEncodingTypes(RSocketMimeType.Json);
        return this;
    }

    public ServiceReferenceBuilder<T> upstreamClusterManager(UpstreamClusterManager upstreamClusterManager) {
        String serviceId = ServiceLocator.gsv(group, service, version);
        UpstreamCluster upstream = upstreamClusterManager.get(serviceId);
        if (upstream == null) {
            upstream = upstreamClusterManager.getBroker();
        }
        this.upstreamCluster = upstream;
        this.sourceUri = upstreamClusterManager.getRequesterSupport().originUri();
        return this;
    }

    public T build() {
        CONSUMED_SERVICES.add(ServiceLocator.of(group, service, version));
        if (RSocketAppContext.ENHANCE) {
            return buildByteBuddyProxy();
        } else {
            return buildJdkProxy();
        }
    }

    /**
     * 构建requester proxy
     */
    private RequesterProxy buildRequesterProxy() {
        return new RequesterProxy(this);
    }

    /**
     * 构建基于jdk代理的requester proxy
     */
    @SuppressWarnings("unchecked")
    private T buildJdkProxy() {
        RequesterProxy proxy = buildRequesterProxy();
        return (T) Proxy.newProxyInstance(
                serviceInterface.getClassLoader(),
                new Class[]{serviceInterface},
                proxy);
    }

    /**
     * 构建基于bytebuddy代理的requester proxy
     */
    private T buildByteBuddyProxy() {
        RequesterProxy proxy = buildRequesterProxy();
        return ByteBuddyUtils.build(this.serviceInterface, proxy);
    }

    //getter
    public URI getSourceUri() {
        return sourceUri;
    }

    public String getGroup() {
        return group;
    }

    public String getService() {
        return service;
    }

    public String getVersion() {
        return version;
    }

    public Duration getCallTimeout() {
        return callTimeout;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public boolean isSticky() {
        return sticky;
    }

    public Class<T> getServiceInterface() {
        return serviceInterface;
    }

    public RSocketMimeType getEncodingType() {
        return encodingType;
    }

    public RSocketMimeType[] getAcceptEncodingTypes() {
        return acceptEncodingTypes;
    }

    public UpstreamCluster getUpstreamCluster() {
        return upstreamCluster;
    }
}
