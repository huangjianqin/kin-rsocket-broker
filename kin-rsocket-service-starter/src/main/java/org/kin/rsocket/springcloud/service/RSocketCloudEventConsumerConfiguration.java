package org.kin.rsocket.springcloud.service;

import org.kin.rsocket.core.event.CloudEventConsumer;
import org.kin.rsocket.core.event.CloudEventConsumers;
import org.kin.rsocket.springcloud.service.event.CloudEvent2ApplicationEventConsumer;
import org.kin.rsocket.springcloud.service.event.InvalidCacheEventConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 注册cloud event consumer相关bean
 * 如果没有绑定rsocket port, 则不会存在cloud event consumer处理cloud event的逻辑, 则不需要注册这些bean
 *
 * @author huangjianqin
 * @date 2021/5/12
 */
@Configuration
@ConditionalOnExpression("${kin.rsocket.port:0} > 0")
public class RSocketCloudEventConsumerConfiguration {
    /**
     * 管理所有{@link CloudEventConsumer}的实例
     */
    @Bean(destroyMethod = "close")
    public CloudEventConsumers cloudEventConsumers(@Autowired List<CloudEventConsumer> consumers) {
        CloudEventConsumers.INSTANCE.addConsumers(consumers);
        return CloudEventConsumers.INSTANCE;
    }

    @Bean
    public CloudEvent2ApplicationEventConsumer cloudEvent2ListenerConsumer() {
        return new CloudEvent2ApplicationEventConsumer();
    }

    @Bean
    public InvalidCacheEventConsumer invalidCacheEventConsumer() {
        return new InvalidCacheEventConsumer();
    }
}
