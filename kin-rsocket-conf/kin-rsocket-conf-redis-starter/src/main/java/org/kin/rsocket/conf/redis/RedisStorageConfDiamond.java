package org.kin.rsocket.conf.redis;

import org.kin.framework.utils.JSON;
import org.kin.framework.utils.PropertiesUtils;
import org.kin.framework.utils.StringUtils;
import org.kin.rsocket.conf.AbstractConfDiamond;
import org.kin.rsocket.core.event.CloudEventNotifyService;
import org.kin.rsocket.core.event.ConfigChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.stream.StreamReceiver;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;

/**
 * @author huangjianqin
 * @date 2021/8/20
 */
public class RedisStorageConfDiamond extends AbstractConfDiamond {
    private static final Logger log = LoggerFactory.getLogger(RedisStorageConfDiamond.class);
    /** key前缀, 标识属于kin-rsocket-broker-conf-diamond写入的配置缓存 */
    private static final String KEY_PREFIX = "kin-rsocket-broker-conf-diamond::";
    /** redis stream name */
    private static final String STREAM_NAME = "kin-rsocket-broker-conf-stream";

    @Resource
    private ReactiveRedisTemplate<String, String> redisTemplate;
    @Resource
    private ReactiveRedisConnectionFactory connectionFactory;
    @Resource
    private CloudEventNotifyService notifyService;
    private Disposable notificationSubscriber;

    /**
     * 监听redis stream, 并根据指定参数更新app配置
     * 例如, XADD kin-rsocket-broker-conf-stream * group mock key application.properties id xxxx
     */
    @PostConstruct
    public void init() {
        StreamReceiver.StreamReceiverOptions<String, MapRecord<String, String, String>> options = StreamReceiver.StreamReceiverOptions.builder().pollTimeout(Duration.ofMillis(500)).build();
        StreamReceiver<String, MapRecord<String, String, String>> receiver = StreamReceiver.create(connectionFactory, options);
        Flux<MapRecord<String, String, String>> notifications = receiver.receive(StreamOffset.latest(STREAM_NAME));
        this.notificationSubscriber = notifications.subscribe(message -> {
            Map<String, String> body = message.getValue();
            String group = body.get("group");
            String key = body.get("key");
            if (StringUtils.isNotBlank(group) && StringUtils.isNotBlank(key)) {
                log.info("received events from Redis stream {} for {}", message.getStream(), group);
                get(group, key).map(value -> {
                    ConfigChangedEvent configChangedEvent = ConfigChangedEvent.of(group, value);
                    return configChangedEvent.toCloudEvent();
                }).subscribe(cloudEvent -> {
                    String jsonText = JSON.write(cloudEvent);
                    if (body.containsKey("appId")) {
                        //如果有appId, 则是针对指定app广播cloud event
                        notifyService.notify(body.get("appId"), jsonText).subscribe();
                    } else {
                        notifyService.notifyAll(group, jsonText).subscribe();
                    }
                });
            }
        });
    }

    @PreDestroy
    public void close() {
        if (notificationSubscriber != null) {
            notificationSubscriber.dispose();
        }
    }

    /**
     * 添加指定前缀
     */
    private String toRedisKey(String group) {
        return KEY_PREFIX.concat(group);
    }

    @Override
    public Flux<String> getGroups() {
        return redisTemplate.keys(KEY_PREFIX);
    }

    @Override
    public Flux<String> findKeysByGroup(String group) {
        return redisTemplate.opsForHash()
                .keys(toRedisKey(group))
                .map(Object::toString);
    }

    @Override
    public Mono<String> findKeyValuesByGroup(String group) {
        return redisTemplate.opsForHash()
                .entries(toRedisKey(group))
                .collectList()
                .map(entries -> {
                    Properties properties = new Properties();
                    for (Map.Entry<Object, Object> entry : entries) {
                        properties.put(entry.getKey(), entry.getValue());
                    }

                    return PropertiesUtils.writePropertiesContent(properties, String.format("app '%s' configs", group));
                }).doOnError(e -> log.error(String.format("conf diamond get all confs from app '%s' error", group), e));
    }

    @Override
    public Mono<Void> put(String group, String key, String value) {
        return redisTemplate.opsForHash()
                .put(toRedisKey(group), key, value)
                .then();
    }

    @Override
    public Mono<Void> remove(String group, String key) {
        return redisTemplate.opsForHash()
                .remove(toRedisKey(group), key)
                .then();
    }

    @Override
    public Mono<String> get(String group, String key) {
        return redisTemplate.opsForHash()
                .get(toRedisKey(group), key)
                .map(Object::toString);
    }
}
