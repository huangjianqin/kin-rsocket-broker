package org.kin.rsocket.core;

import io.cloudevents.CloudEvent;
import org.kin.framework.utils.ClassUtils;
import org.kin.framework.utils.MurmurHash3;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.core.domain.RSocketServiceInfo;
import org.kin.rsocket.core.domain.ReactiveMethodInfo;
import org.kin.rsocket.core.domain.ReactiveMethodParameterInfo;
import org.kin.rsocket.core.event.RSocketServicesExposedEvent;
import org.kin.rsocket.core.health.HealthCheck;
import org.kin.rsocket.core.utils.Separators;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * 服务注册表, 单例, 仅仅是rsocket service内部用于存储provider方法handler映射
 * <p>
 * handler = 可以是方法名, 也可以是自定义名字
 * handlerId = hash(service.handler)
 * <p>
 * 一个app仅仅只有一个service name的instance, 不支持不同组多版本在同一jvm上注册
 *
 * @author huangjianqin
 * @date 2021/3/27
 */
public final class LocalRSocketServiceRegistry implements RSocketServiceInfoSupport {
    public static final LocalRSocketServiceRegistry INSTANCE = new LocalRSocketServiceRegistry();

    /**
     * @return exposed services信息
     */
    public static Set<ServiceLocator> exposedServices() {
        return LocalRSocketServiceRegistry.INSTANCE.findAllServiceLocators()
                .stream()
                //过滤掉local service
                .filter(l -> !l.getService().equals(HealthCheck.class.getName()))
                .collect(Collectors.toSet());
    }

    /**
     * @return services exposed cloud event
     */
    @Nullable
    public static CloudEvent servicesExposedCloudEvent() {
        Collection<ServiceLocator> serviceLocators = exposedServices();
        if (serviceLocators.isEmpty()) {
            return null;
        }

        return RSocketServicesExposedEvent.of(serviceLocators).toCloudEvent();
    }

    /** 修改数据需加锁 */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    /** key -> service, value -> provider, 即service instance */
    private volatile Map<String, Object> service2Provider = new HashMap<>();
    /** key -> hash(service.method), value -> service method invoker */
    private volatile Map<Integer, ReactiveMethodInvoker> handlerId2Invoker = new HashMap<>();
    /** key -> service, value -> reactive service info */
    private volatile Map<String, RSocketServiceInfo> service2Info = new HashMap<>();

    private LocalRSocketServiceRegistry() {
        //用于broker可以请求service instance访问其指定服务详细信息
        addProvider("", "", RSocketServiceInfoSupport.class, this, "暴露RSocket Service信息的服务");
    }

    /**
     * 是否包含对应hash(service.handler)注册信息
     */
    public boolean contains(int handlerId) {
        return handlerId2Invoker.containsKey(handlerId);
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
        return service2Provider.containsKey(service);
    }

    /**
     * 返回所有已注册的服务
     *
     * @return 所有已注册的服务
     */
    public Set<String> findAllServices() {
        return new HashSet<>(service2Provider.keySet());
    }

    /**
     * 返回所有已注册的服务
     *
     * @return 所有已注册的服务
     */
    public Set<ServiceLocator> findAllServiceLocators() {
        return service2Info.values().stream()
                .map(i -> ServiceLocator.of(i.getGroup(), i.getService(), i.getVersion()))
                .collect(Collectors.toSet());
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
            //copy
            Map<String, Object> service2Provider = new HashMap<>(this.service2Provider);
            Map<Integer, ReactiveMethodInvoker> handlerId2Invoker = new HashMap<>(this.handlerId2Invoker);
            Map<String, RSocketServiceInfo> service2Info = new HashMap<>(this.service2Info);

            for (Method method : interfaceClass.getMethods()) {
                if (method.isDefault()) {
                    continue;
                }

                String handler = method.getName();
                String key = service + Separators.SERVICE_HANDLER + handler;

                ReactiveMethodInvoker invoker = new ReactiveMethodInvoker(method, provider);

                service2Provider.put(service, provider);
                handlerId2Invoker.put(MurmurHash3.hash32(key), invoker);
                service2Info.put(service, newReactiveServiceInfo(group, service, version, interfaceClass, tags));
            }

            //update
            this.service2Provider = service2Provider;
            this.handlerId2Invoker = handlerId2Invoker;
            this.service2Info = service2Info;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 注册service, 不会主动往broker注册新增服务
     * 供cloud function使用, 因为其无法获取真实的service信息, 所以, 只能在外部从spring function registry中尽量提取service信息, 然后进行注册
     */
    public void addProvider(String handler,
                            Object provider, ReactiveMethodInvoker invoker, RSocketServiceInfo serviceInfo) {
        Lock writeLock = this.lock.writeLock();
        writeLock.lock();
        try {
            String service = serviceInfo.getService();
            String key = service + Separators.SERVICE_HANDLER + handler;

            //copy
            Map<String, Object> service2Provider = new HashMap<>(this.service2Provider);
            Map<Integer, ReactiveMethodInvoker> handlerId2Invoker = new HashMap<>(this.handlerId2Invoker);
            Map<String, RSocketServiceInfo> service2Info = new HashMap<>(this.service2Info);

            service2Provider.put(service, provider);
            handlerId2Invoker.put(MurmurHash3.hash32(key), invoker);
            service2Info.put(service, serviceInfo);

            //update
            this.service2Provider = service2Provider;
            this.handlerId2Invoker = handlerId2Invoker;
            this.service2Info = service2Info;
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
            //copy
            Map<String, Object> service2Provider = new HashMap<>(this.service2Provider);
            Map<Integer, ReactiveMethodInvoker> handlerId2Invoker = new HashMap<>(this.handlerId2Invoker);
            Map<String, RSocketServiceInfo> service2Info = new HashMap<>(this.service2Info);

            service2Provider.remove(service);
            for (Method method : serviceInterface.getMethods()) {
                if (method.isDefault()) {
                    continue;
                }

                String handler = method.getName();
                String key = service + Separators.SERVICE_HANDLER + handler;

                handlerId2Invoker.remove(MurmurHash3.hash32(key));
                service2Info.remove(service);
            }

            //update
            this.service2Provider = service2Provider;
            this.handlerId2Invoker = handlerId2Invoker;
            this.service2Info = service2Info;
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
        return handlerId2Invoker.get(handlerId);
    }

    /**
     * 是否包含指定method invoker
     */
    public boolean containsHandler(int handlerId) {
        return handlerId2Invoker.containsKey(handlerId);
    }

    /**
     * 用于后台访问服务接口具体信息
     */
    @Override
    public RSocketServiceInfo getReactiveServiceInfoByName(String service) {
        return service2Info.get(service);
    }
}
