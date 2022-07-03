package org.kin.rsocket.core;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.lang.annotation.*;

/**
 * 标识rsocket service
 * 目前支持两种方式注册rsocket service
 * 1. 使用{@link RSocketService}注解在rsocket service实现类上
 * <pre class="code">
 * &#64;RSocketService(UserService.class)
 * public class UserServiceImpl implements UserService {
 *     ......
 * }
 * </pre>
 * 2. {@link Bean}+{@link RSocketService}创建rsocket service bean
 * <pre class="code">
 * &#64;Configuration
 * public class ResponderConfiguration {
 *     &#64;Bean
 *     &#64;RSocketService(UserService.class)
 *     public UserService userService(){
 *         return new UserServiceImpl();
 *     }
 * }
 * </pre>
 *
 * @author huangjianqin
 * @date 2021/3/28
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Service
public @interface RSocketService {
    /**
     * service interface
     *
     * @return service interface
     */
    Class<?> value();

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
     * service tags
     *
     * @return labels
     */
    String[] tags() default {};
}