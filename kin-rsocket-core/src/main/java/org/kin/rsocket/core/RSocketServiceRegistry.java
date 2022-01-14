package org.kin.rsocket.core;

import org.kin.framework.utils.ClassUtils;
import org.kin.framework.utils.MurmurHash3;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.core.domain.RSocketServiceInfo;
import org.kin.rsocket.core.domain.ReactiveMethodInfo;
import org.kin.rsocket.core.domain.ReactiveMethodParameterInfo;
import org.kin.rsocket.core.event.CloudEventData;
import org.kin.rsocket.core.event.RSocketServicesExposedEvent;
import org.kin.rsocket.core.health.HealthCheck;
import org.kin.rsocket.core.utils.Separators;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * 服务注册表, 单例, 仅仅是内部用于存储方法handler映射
 * <p>
 * handler = 可以是方法名, 也可以是自定义名字
 * handlerId = hash(service.handler)
 * <p>
 * 一个app仅仅只有一个service name的instance, 不支持不同组多版本在同一jvm上注册
 *
 * @author huangjianqin
 * @date 2021/3/27
 */
public final class RSocketServiceRegistry implements RSocketServiceInfoSupport {
    public static final RSocketServiceRegistry INSTANCE = new RSocketServiceRegistry();

    /**
     * @return exposed services信息
     */
    public static Set<ServiceLocator> exposedServices() {
        return RSocketServiceRegistry.INSTANCE.findAllServiceLocators()
                .stream()
                //过滤掉local service
                .filter(l -> !l.getService().equals(HealthCheck.class.getName()))
                .collect(Collectors.toSet());
    }

    /**
     * @return services exposed cloud event
     */
    public static CloudEventData<RSocketServicesExposedEvent> servicesExposedEvent() {
        Collection<ServiceLocator> serviceLocators = exposedServices();
        if (serviceLocators.isEmpty()) {
            return null;
        }

        return RSocketServicesExposedEvent.of(serviceLocators);
    }

    /** 修改数据需加锁 */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    /** key -> service, value -> provider, 即service instance */
    private final Map<String, Object> service2Provider = new HashMap<>();
    /** key -> hash(service.method), value -> service method invoker */
    private final Map<Integer, ReactiveMethodInvoker> handlerId2Invoker = new HashMap<>();
    /** key -> service, value -> reactive service info */
    private final Map<String, RSocketServiceInfo> service2Info = new HashMap<>();

    private RSocketServiceRegistry() {
        //用于broker可以请求service instance访问其指定服务详细信息
        addProvider("", "", RSocketServiceInfoSupport.class, this, "暴露RSocket Service信息的服务");
    }

    /**
     * 是否包含对应hash(service.handler)注册信息
     */
    public boolean contains(int handlerId) {
        Lock readLock = lock.readLock();
        readLock.lock();
        try {
            return handlerId2Invoker.containsKey(handlerId);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 是否包含对应service注册信息
     */
    public boolean contains(String service, String handler) {
        return contains(MurmurHash3.hash32(service + Separators.SERVICE_HANDLER + handler));
    }

    /**
     * 是否包含对应handlerId的注册信息
     */
    public boolean contains(String service) {
        Lock readLock = lock.readLock();
        readLock.lock();
        try {
            return service2Provider.containsKey(service);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 返回所有已注册的服务
     *
     * @return 所有已注册的服务
     */
    public Set<String> findAllServices() {
        Lock readLock = lock.readLock();
        readLock.lock();
        try {
            return new HashSet<>(service2Provider.keySet());
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 返回所有已注册的服务
     *
     * @return 所有已注册的服务
     */
    public Set<ServiceLocator> findAllServiceLocators() {
        Lock readLock = lock.readLock();
        readLock.lock();
        try {
            return service2Info.values().stream()
                    .map(i -> ServiceLocator.of(i.getGroup(), i.getService(), i.getVersion()))
                    .collect(Collectors.toSet());
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 注册service, 不会主动往broker注册新增服务
     */
    public void addProvider(String group, String version, Class<?> interfaceClass, Object provider, String... tags) {
        addProvider(group, interfaceClass.getName(), version, interfaceClass, provider, tags);
    }

    /**
     * 注册service, 不会主动往broker注册新增服务
     *
     * @param tags 对应{@link RSocketService#tags()}
     */
    public void addProvider(String group, String service, String version, Class<?> interfaceClass, Object provider, String... tags) {
        Lock writeLock = this.lock.writeLock();
        writeLock.lock();
        try {
            for (Method method : interfaceClass.getMethods()) {
                if (!method.isDefault()) {
                    String handler = method.getName();
                    //解析ServiceMapping注解, 看看是否自定义method(handler) name
                    ServiceMapping serviceMapping = method.getAnnotation(ServiceMapping.class);
                    if (Objects.nonNull(serviceMapping) && StringUtils.isNotBlank(serviceMapping.value())) {
                        handler = serviceMapping.value();
                    }
                    String key = service + Separators.SERVICE_HANDLER + handler;

                    ReactiveMethodInvoker invoker = new ReactiveMethodInvoker(method, provider);

                    service2Provider.put(service, provider);
                    handlerId2Invoker.put(MurmurHash3.hash32(key), invoker);
                    service2Info.put(service, newReactiveServiceInfo(group, service, version, interfaceClass, tags));
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 注册service, 不会主动往broker注册新增服务
     * 供cloud function使用, 因为其无法获取真实的service信息, 所以, 只能在外部从spring function registry中尽量提取service信息, 然后进行注册
     */
    public void addProvider(String service, String handler,
                            Object provider, ReactiveMethodInvoker invoker, RSocketServiceInfo serviceInfo) {
        Lock writeLock = this.lock.writeLock();
        writeLock.lock();
        try {
            String key = service + Separators.SERVICE_HANDLER + handler;

            service2Provider.put(service, provider);
            handlerId2Invoker.put(MurmurHash3.hash32(key), invoker);
            service2Info.put(service, serviceInfo);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 创建reactive service信息, 用于后台访问服务接口具体信息
     */
    private RSocketServiceInfo newReactiveServiceInfo(String group, String service,
                                                      String version, Class<?> interfaceClass,
                                                      String[] tags) {
        RSocketServiceInfo.Builder builder = RSocketServiceInfo.builder();
        builder.group(group);
        builder.version(version);
        if (interfaceClass.getPackage() != null) {
            builder.namespace(interfaceClass.getPackage().getName());
        }
        builder.name(interfaceClass.getName());
        builder.service(service);

        Desc desc = interfaceClass.getAnnotation(Desc.class);
        if (Objects.nonNull(desc) && StringUtils.isNotBlank(desc.value())) {
            builder.description(desc.value());
        }

        Deprecated interfaceDeprecated = interfaceClass.getAnnotation(Deprecated.class);
        if (interfaceDeprecated != null) {
            builder.deprecated(true);
        }

        List<ReactiveMethodInfo> methodInfos = new ArrayList<>(8);
        for (Method method : interfaceClass.getMethods()) {
            if (method.isDefault() || Modifier.isStatic(method.getModifiers())) {
                //过滤掉default 和 static 方法
                continue;
            }

            methodInfos.add(newReactiveMethodInfo(method));
        }
        builder.methods(methodInfos);
        builder.tags(tags);

        return builder.build();
    }

    /**
     * 创建reactive service method信息
     */
    private ReactiveMethodInfo newReactiveMethodInfo(Method method) {
        ReactiveMethodInfo.Builder builder = ReactiveMethodInfo.builder();

        Desc desc = method.getAnnotation(Desc.class);
        if (Objects.nonNull(desc) && StringUtils.isNotBlank(desc.value())) {
            builder.description(desc.value());
        }

        Deprecated methodDeprecated = method.getAnnotation(Deprecated.class);
        if (methodDeprecated != null) {
            builder.deprecated(true);
        }
        builder.name(method.getName());
        builder.returnType(method.getReturnType().getName());
        builder.returnInferredType(ClassUtils.getInferredClassForGeneric(method.getGenericReturnType()).getName());

        List<ReactiveMethodParameterInfo> parameterInfos = new ArrayList<>(4);
        for (Parameter parameter : method.getParameters()) {
            parameterInfos.add(newReactiveMethodParameterInfo(parameter));
        }
        builder.parameters(parameterInfos);

        return builder.build();
    }

    /**
     * 创建reactive service parameter信息
     */
    private ReactiveMethodParameterInfo newReactiveMethodParameterInfo(Parameter parameter) {
        ReactiveMethodParameterInfo.Builder builder = ReactiveMethodParameterInfo.builder();

        Desc desc = parameter.getAnnotation(Desc.class);
        if (Objects.nonNull(desc) && StringUtils.isNotBlank(desc.value())) {
            builder.description(desc.value());
        }

        Required required = parameter.getAnnotation(Required.class);
        if (Objects.nonNull(required)) {
            builder.required(true);
        }

        builder.name(parameter.getName());
        String type = parameter.getType().getName();
        builder.type(type);
        String inferredType = ClassUtils.getInferredClassForGeneric(parameter.getParameterizedType()).getName();
        if (!type.equals(inferredType)) {
            builder.inferredType(inferredType);
        }

        return builder.build();
    }

    /**
     * 注销service信息, , 不会主动往broker注销移除服务
     */
    public void removeProvider(String group, String service, String version, Class<?> serviceInterface) {
        Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            service2Provider.remove(service);
            for (Method method : serviceInterface.getMethods()) {
                if (!method.isDefault()) {
                    String handler = method.getName();
                    ServiceMapping serviceMapping = method.getAnnotation(ServiceMapping.class);
                    if (Objects.nonNull(serviceMapping) && StringUtils.isNotBlank(serviceMapping.value())) {
                        handler = serviceMapping.value();
                    }
                    String key = service + Separators.SERVICE_HANDLER + handler;

                    handlerId2Invoker.remove(MurmurHash3.hash32(key));
                    service2Info.remove(service);
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 返回method invoker
     */
    public ReactiveMethodInvoker getInvoker(String service, String handler) {
        return getInvoker(MurmurHash3.hash32(service + Separators.SERVICE_HANDLER + handler));
    }

    /**
     * 返回method invoker
     */
    public ReactiveMethodInvoker getInvoker(int handlerId) {
        Lock readLock = lock.readLock();
        readLock.lock();
        try {
            return handlerId2Invoker.get(handlerId);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 是否包含指定method invoker
     */
    public boolean containsHandler(int handlerId) {
        Lock readLock = lock.readLock();
        readLock.lock();
        try {
            return handlerId2Invoker.containsKey(handlerId);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 用于后台访问服务接口具体信息
     */
    @Override
    public RSocketServiceInfo getReactiveServiceInfoByName(String service) {
        Lock readLock = lock.readLock();
        readLock.lock();
        try {
            return service2Info.get(service);
        } finally {
            readLock.unlock();
        }
    }
}
