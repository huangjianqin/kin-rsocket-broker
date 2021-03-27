package org.kin.rsocket.core;

import com.google.common.base.Preconditions;
import org.kin.rsocket.core.metadata.RSocketMimeType;
import org.kin.rsocket.core.utils.ByteBuddyUtils;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * @author huangjianqin
 * @date 2021/3/27
 */
public class RemoteServiceBuilder<T> {
    /** todo 缓存的所有requester proxy, 用于监控 */
    public static final Set<ServiceLocator> CONSUMED_SERVICES = new HashSet<>();
    public static boolean enhance = true;

    static {
        try {
            Class.forName("net.bytebuddy.ByteBuddy");
        } catch (Exception e) {
            enhance = false;
        }
    }

    /** uri */
    private URI sourceUri;
    /** group */
    private String group;
    /** service */
    private String service;
    /** version */
    private String version;
    /** call timeout todo 是否需要支持配置 */
    private Duration timeout = Duration.ofMillis(3000);
    /** endpoint */
    private String endpoint;
    /** sticky session */
    private boolean sticky;
    /** 服务接口 */
    private Class<T> serviceInterface;
    /** 数据编码类型 */
    private RSocketMimeType encodingType = RSocketMimeType.Java_Object;
    /** accept 编码类型 */
    private RSocketMimeType acceptEncodingType;
    /** 对应的upstream cluster */
    private UpstreamCluster upstreamCluster;

    private RemoteServiceBuilder() {
    }

    public static <T> RemoteServiceBuilder<T> requester(Class<T> serviceInterface) {
        RemoteServiceBuilder<T> builder = new RemoteServiceBuilder<T>();
        builder.serviceInterface = serviceInterface;
        builder.service = serviceInterface.getCanonicalName();
        //解析@ServiceMapping注解
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
            if (!serviceMapping.resultEncoding().isEmpty()) {
                builder.acceptEncodingType = RSocketMimeType.getByType(serviceMapping.resultEncoding());
            }
            builder.sticky = serviceMapping.sticky();
        }
        return builder;
    }

    public RemoteServiceBuilder<T> group(String group) {
        this.group = group;
        return this;
    }

    public RemoteServiceBuilder<T> service(String service) {
        this.service = service;
        return this;
    }

    public RemoteServiceBuilder<T> version(String version) {
        this.version = version;
        return this;
    }

    public RemoteServiceBuilder<T> timeoutMillis(int millis) {
        this.timeout = Duration.ofMillis(millis);
        return this;
    }

    public RemoteServiceBuilder<T> endpoint(String endpoint) {
        Preconditions.checkArgument(endpoint.contains(":"));
        this.endpoint = endpoint;
        return this;
    }

    public RemoteServiceBuilder<T> sticky(boolean sticky) {
        this.sticky = sticky;
        return this;
    }

    /**
     * 一般使用{@link #upstreamClusterManager(UpstreamClusterManager)}, 因为其会自动寻找对应serviceId的UpstreamCluster
     */
    public RemoteServiceBuilder<T> upstream(UpstreamCluster upstreamCluster) {
        this.upstreamCluster = upstreamCluster;
        return this;
    }

    public RemoteServiceBuilder<T> encodingType(RSocketMimeType encodingType) {
        this.encodingType = encodingType;
        return this;
    }

    public RemoteServiceBuilder<T> acceptEncodingType(RSocketMimeType encodingType) {
        this.acceptEncodingType = encodingType;
        return this;
    }

    /**
     * GraalVM nativeImage support: set encodeType and acceptEncodingType to Json
     *
     * @return this
     */
    public RemoteServiceBuilder<T> nativeImage() {
        this.encodingType = RSocketMimeType.Json;
        this.acceptEncodingType = RSocketMimeType.Json;
        return this;
    }

    public RemoteServiceBuilder<T> upstreamClusterManager(UpstreamClusterManager upstreamClusterManager) {
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
        if (enhance) {
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
        CONSUMED_SERVICES.add(new ServiceLocator(group, service, version));
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
        CONSUMED_SERVICES.add(new ServiceLocator(group, service, version));
        RequesterProxy proxy = buildRequesterProxy();
        return ByteBuddyUtils.build(this.serviceInterface, proxy);
    }

    //getter
    public static boolean isEnhance() {
        return enhance;
    }

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

    public Duration getTimeout() {
        return timeout;
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

    public RSocketMimeType getAcceptEncodingType() {
        return acceptEncodingType;
    }

    public UpstreamCluster getUpstreamCluster() {
        return upstreamCluster;
    }
}
