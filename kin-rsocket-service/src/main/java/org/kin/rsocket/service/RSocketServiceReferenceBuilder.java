package org.kin.rsocket.service;

import brave.Tracing;
import com.google.common.base.Preconditions;
import org.kin.framework.collection.ConcurrentHashSet;
import org.kin.framework.utils.CollectionUtils;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.core.*;
import org.kin.rsocket.service.utils.ByteBuddyUtils;
import org.springframework.core.annotation.AnnotationAttributes;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * @author huangjianqin
 * @date 2021/3/27
 */
public final class RSocketServiceReferenceBuilder<T> {
    /** 缓存的所有requester proxy, 用于监控 */
    public static final Set<ServiceLocator> CONSUMED_SERVICES = new ConcurrentHashSet<>();

    /** uri */
    private URI sourceUri;
    /** group */
    private String group = "";
    /** service */
    private String service = "";
    /** version */
    private String version = "";
    /** call timeout, 默认3s */
    private Duration callTimeout = Duration.ofMillis(3000);
    /**
     * endpoint
     * 形式:
     * 1. id:XX
     * 2. uuid:XX
     * 3. ip:XX
     */
    private String endpoint = "";
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
    private RSocketMimeType encodingType = RSocketMimeType.JSON;
    /** accept 编码类型 */
    private RSocketMimeType[] acceptEncodingTypes = new RSocketMimeType[]{RSocketMimeType.JSON};
    /** 选择一个合适的{@link UpstreamCluster}(可broker可直连)的selector */
    private UpstreamClusterSelector selector;
    /** consumer是否开启p2p */
    private boolean p2p;
    /** zipkin */
    private Tracing tracing;

    private RSocketServiceReferenceBuilder() {
    }

    /**
     * !!!注意: 如果自己使用{@link RSocketServiceReferenceBuilder}来创建bean, 不会使用{@link RSocketServiceProperties#getGroup()}和
     * {@link RSocketServiceProperties#getVersion()}来作为全局默认group或者version. 想要达到该效果, 需要手动设置
     */
    public static <T> RSocketServiceReferenceBuilder<T> requester(Class<T> serviceInterface) {
        RSocketServiceReferenceBuilder<T> builder = new RSocketServiceReferenceBuilder<>();
        builder.serviceInterface = serviceInterface;
        builder.service = serviceInterface.getName();
        return builder;
    }

    /**
     * 适用于基于spring容器启动的Application
     * 会读取注解在field或者bean factory method下{@link RSocketServiceReference}的属性生成builder实例
     */
    public static <T> RSocketServiceReferenceBuilder<T> requester(Class<T> serviceInterface, AnnotationAttributes annoAttrs) {
        RSocketServiceReferenceBuilder<T> builder = requester(serviceInterface);

        String service = annoAttrs.getString("name");
        if (StringUtils.isNotBlank(service)) {
            builder.service(service);
        }

        String group = annoAttrs.getString("group");
        if (StringUtils.isNotBlank(group)) {
            builder.group(group);
        }

        String version = annoAttrs.getString("version");
        if (StringUtils.isNotBlank(version)) {
            builder.version(version);
        }

        int callTimeout = annoAttrs.getNumber("callTimeout");
        if (callTimeout > 0) {
            builder.callTimeout(callTimeout);
        }

        String endpoint = annoAttrs.getString("endpoint");
        if (StringUtils.isNotBlank(endpoint)) {
            builder.endpoint(endpoint);
        }

        boolean sticky = annoAttrs.getBoolean("sticky");
        if (sticky) {
            builder.sticky();
        }

        RSocketMimeType encodingType = annoAttrs.getEnum("encodingType");
        builder.encodingType(encodingType);

        RSocketMimeType[] acceptEncodingTypes = (RSocketMimeType[]) annoAttrs.get("acceptEncodingTypes");
        if (CollectionUtils.isNonEmpty(acceptEncodingTypes)) {
            builder.acceptEncodingTypes(acceptEncodingTypes);
        }

        boolean p2p = annoAttrs.getBoolean("p2p");
        if (p2p) {
            builder.p2p();
        }

        return builder;
    }

    public RSocketServiceReferenceBuilder<T> group(String group) {
        this.group = group;
        return this;
    }

    /**
     * group is empty才替换
     */
    public RSocketServiceReferenceBuilder<T> groupIfEmpty(String group) {
        if (StringUtils.isBlank(group)) {
            return group(group);
        }
        return this;
    }

    public RSocketServiceReferenceBuilder<T> service(String service) {
        this.service = service;
        return this;
    }

    public RSocketServiceReferenceBuilder<T> version(String version) {
        this.version = version;
        return this;
    }

    /**
     * version is empty才替换
     */
    public RSocketServiceReferenceBuilder<T> versionIfEmpty(String version) {
        if (StringUtils.isBlank(version)) {
            return version(version);
        }
        return this;
    }

    public RSocketServiceReferenceBuilder<T> callTimeout(int millis) {
        this.callTimeout = Duration.ofMillis(millis);
        return this;
    }

    public RSocketServiceReferenceBuilder<T> endpoint(String endpoint) {
        Preconditions.checkArgument(endpoint.contains(":"));
        this.endpoint = endpoint;
        return this;
    }

    /**
     * 该consumer全部接口方法都开启sticky session
     */
    public RSocketServiceReferenceBuilder<T> sticky() {
        this.sticky = true;
        return this;
    }

    /**
     * 一般使用{@link #upstreamClusterManager(UpstreamClusterManager)}, 因为其会自动寻找对应serviceId的UpstreamCluster
     */
    public RSocketServiceReferenceBuilder<T> upstream(UpstreamCluster upstreamCluster) {
        this.selector = upstreamCluster;
        group(upstreamCluster.getGroup());
        service(upstreamCluster.getService());
        version(upstreamCluster.getVersion());
        return this;
    }

    public RSocketServiceReferenceBuilder<T> encodingType(RSocketMimeType encodingType) {
        RSocketMimeType.checkEncodingMimeType(encodingType);
        this.encodingType = encodingType;
        return this;
    }

    public RSocketServiceReferenceBuilder<T> acceptEncodingTypes(RSocketMimeType... acceptEncodingTypes) {
        if (CollectionUtils.isEmpty(acceptEncodingTypes)) {
            return this;
        }

        for (RSocketMimeType acceptEncodingType : acceptEncodingTypes) {
            RSocketMimeType.checkEncodingMimeType(acceptEncodingType);
        }

        this.acceptEncodingTypes = acceptEncodingTypes;
        return this;
    }

    /**
     * GraalVM nativeImage support: set encodeType and acceptEncodingType to Json
     */
    public RSocketServiceReferenceBuilder<T> nativeImage() {
        encodingType(RSocketMimeType.JSON);
        acceptEncodingTypes(RSocketMimeType.JSON);
        return this;
    }

    /**
     * 如果配置了rsocket service endpoint, 则使用该connection, 否则使用broker connection, 然后路由回对应的rsocket service app处理
     */
    public RSocketServiceReferenceBuilder<T> upstreamClusterManager(UpstreamClusterManager upstreamClusterManager) {
        this.selector = upstreamClusterManager;
        this.sourceUri = upstreamClusterManager.getRequesterSupport().originUri();
        return this;
    }

    /**
     * consumer开启p2p直连
     */
    public RSocketServiceReferenceBuilder<T> p2p() {
        this.p2p = true;
        return this;
    }

    /**
     * 开启zipkin
     */
    public RSocketServiceReferenceBuilder<T> tracing(Tracing tracing) {
        this.tracing = tracing;
        return this;
    }

    public T build() {
        ServiceLocator serviceLocator = ServiceLocator.of(group, service, version);

        check(serviceLocator);

        if (selector instanceof UpstreamClusterManager && p2p) {
            ((UpstreamClusterManager) selector).openP2p(serviceLocator.getGsv());
        }

        CONSUMED_SERVICES.add(serviceLocator);
        if (RSocketAppContext.ENHANCE) {
            return buildByteBuddyProxy();
        } else {
            return buildJdkProxy();
        }
    }

    /**
     * 合法性检查
     */
    private void check(ServiceLocator serviceLocator) {
        if (selector instanceof UpstreamCluster) {
            UpstreamCluster upstreamCluster = (UpstreamCluster) this.selector;
            if (!serviceLocator.getGsv().equals(upstreamCluster.getServiceId())) {
                //检查构建的服务reference service gsv与builder指定的gsv是否一致
                throw new IllegalStateException("UpstreamCluster's service gsv must be match RSocketServiceReferenceBuilder's service gsv");
            }
        }
    }

    /**
     * 构建requester proxy
     */
    private RequesterProxy buildRequesterProxy() {
        if (Objects.nonNull(tracing)) {
            return new ZipkinRequesterProxy(this);
        } else {
            return new RequesterProxy(this);
        }
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

    public UpstreamClusterSelector getSelector() {
        return selector;
    }

    public boolean isP2p() {
        return p2p;
    }

    public Tracing getTracing() {
        return tracing;
    }
}
