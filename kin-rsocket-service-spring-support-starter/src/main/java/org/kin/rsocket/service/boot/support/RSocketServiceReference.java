package org.kin.rsocket.service.boot.support;

import org.springframework.context.annotation.Bean;

import java.lang.annotation.*;

/**
 * 标识并创建rsocket service reference
 * 3种使用方式:
 * 1. {@link Bean}+{@link RSocketServiceReferenceBuilder}构建rsocket service reference
 * <pre class="code">
 * &#64;Configuration
 * public class RequesterConfiguration {
 *     &#64;Bean
 *     public UserService userService(@Autowired RSocketRequester rsocketRequester) {
 *         return RSocketServiceReferenceBuilder
 *                 .reference(rsocketRequester, UserService.class)
 *                 .build();
 *     }
 * }
 * </pre>
 * 2. {@link Bean}+{@link RSocketServiceReference}构建rsocket service reference
 * <pre class="code">
 * &#64;Configuration
 * public class RequesterConfiguration {
 *     &#64;Bean
 *     &#64;RSocketServiceReference(interfaceClass = UserService.class, appName = "XXXX")
 *     public RSocketServiceReferenceFactoryBean<UserService> userService() {
 *         return new RSocketServiceReferenceFactoryBean<>();
 *     }
 * }
 * </pre>
 * 3. 使用{@link RSocketServiceReference}注解在字段变量上构建rsocket service reference
 * <pre class="code">
 * &#64;RestController
 * public class UserController {
 *     &#64;RSocketServiceReference(appName = "XXXX")
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
     * 服务名
     */
    String service() default "";

    /**
     * call timeout, 默认3s
     */
    int callTimeout() default 3000;

    /**
     * naming service上注册的rsocket service application name
     * 目前字段仅仅在使用{@link EnableLBRSocketServiceReference}前提下有效
     *
     * @see EnableLBRSocketServiceReference
     */
    String appName() default "";
}
