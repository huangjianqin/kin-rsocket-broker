package org.kin.rsocket.service;

import org.kin.rsocket.core.Endpoints;
import org.kin.rsocket.core.RSocketMimeType;
import org.springframework.context.annotation.Bean;

import java.lang.annotation.*;

/**
 * 标识并创建rsocket service reference
 * 注意: 最终rsocket service reference的bean name是其service name, 所以注意不要重复, 不然会启动错误
 * <p>
 * 3种使用方式:
 * 1. {@link Bean}+{@link RSocketServiceReferenceBuilder}构建rsocket service reference
 * <pre class="code">
 * &#64;Configuration
 * public class RequesterConfiguration {
 *     &#64;Bean
 *     public UserService userService(@Autowired RSocketBrokerClient brokerClient) {
 *         return RSocketServiceReferenceBuilder
 *                 .reference(UserService.class)
 *                 .upstreamClusters(brokerClient)
 *                 .build();
 *     }
 * }
 * </pre>
 * 2. {@link Bean}+{@link RSocketServiceReference}构建rsocket service reference
 * <pre class="code">
 * &#64;Configuration
 * public class RequesterConfiguration {
 *     &#64;Bean
 *     &#64;RSocketServiceReference(interfaceClass = UserService.class)
 *     public RSocketServiceReferenceFactoryBean<UserService> userService() {
 *         return new RSocketServiceReferenceFactoryBean<>();
 *     }
 * }
 * </pre>
 * 3. 使用{@link RSocketServiceReference}注解在字段变量上构建rsocket service reference
 * <pre class="code">
 * &#64;RestController
 * public class UserController {
 *     &#64;RSocketServiceReference
 *     private UserService userService;
 * }
 * </pre>
 *
 * @author huangjianqin
 * @date 2021/5/19
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RSocketServiceReference {
    /**
     * 返回rsocket service class, 目前仅仅方法2需要开发者指出
     *
     * @return service interface
     */
    Class<?> interfaceClass() default Void.class;

    /**
     * service name
     *
     * @return service name
     */
    String name() default "";

    /**
     * service group
     *
     * @return group
     */
    String group() default "";

    /**
     * service version
     *
     * @return version
     */
    String version() default "";

    /**
     * call timeout, 默认3s
     */
    int callTimeout() default 3000;

    /**
     * endpoint, 用于直接匹配应用唯一tag
     * 内置提供的定义请看
     *
     * @see Endpoints
     */
    String endpoint() default "";

    /**
     * sticky session
     * 相当于固定session, 指定service首次请求后, 后面请求都是route到该service instance
     * 如果该service instance失效, 重新选择一个sticky service instance
     * 目前仅仅在service mesh校验通过下才允许mark sticky service instance
     */
    boolean sticky() default false;

    /** 数据编码类型 */
    RSocketMimeType encodingType() default RSocketMimeType.JSON;

    /** accept 编码类型 */
    RSocketMimeType[] acceptEncodingTypes() default {RSocketMimeType.JSON};

    /** consumer 是否开启p2p直连模式 */
    boolean p2p() default false;
}
