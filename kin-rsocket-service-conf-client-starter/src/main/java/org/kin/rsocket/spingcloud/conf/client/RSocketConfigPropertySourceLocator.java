package org.kin.rsocket.spingcloud.conf.client;

import org.kin.framework.utils.ExceptionUtils;
import org.kin.framework.utils.PropertiesUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.util.Objects;
import java.util.Properties;

/**
 * RSocket Config properties source locator from RSocket Broker
 *
 * @author huangjianqin
 * @date 2021/4/20
 */
public class RSocketConfigPropertySourceLocator implements PropertySourceLocator {
    private static final Logger log = LoggerFactory.getLogger(RSocketConfigPropertySourceLocator.class);
    /** 上次配置刷新的properties内容 */
    private String lastContent = "";
    /**
     * 配置内容
     * 配置变化时, {@link ConfigChangedEventConsumer}实例会直接修改该字段内容, 然后refresh context
     */
    private Properties confs;
    /** 配置source */
    private PropertiesPropertySource source;

    @Override
    public PropertySource<?> locate(Environment environment) {
        /**
         * 没有下面三个字段信息, 无法从broker那获取到具体的配置信息
         *
         * ContextRefresher.refresh时, 只会重新加载bootstrap.yml配置, 不包括application.yml, 所有下面是三个配置信息必须要在bootstrap.yml里面定义
         * 可以通过定义spring.cloud.refresh.additionalPropertySourcesToRetain, 让copy environment时, 把指定配置的内容也copy过来
         *
         * 如果bootstrap.yml没有任何内容, 则会去加载application.yml, 但bootstrap.yml有任何内容, 则不会先加载application.yml
         * 因此仅仅有application.yml也可以remote fetch configs, 但不支持刷新
         */
        String jwtToken = environment.getProperty("kin.rsocket.jwt-token");
        String rsocketBrokers = environment.getProperty("kin.rsocket.brokers");
        String applicationName = environment.getProperty("spring.application.name");

        if (Objects.nonNull(this.source)) {
            return source;
        }

        if (jwtToken != null && rsocketBrokers != null && applicationName != null) {
            Properties confs = new Properties();
            for (String rsocketBroker : rsocketBrokers.split(",")) {
                URI rsocketUri = URI.create(rsocketBroker);
                //首次通过http请求获取
                String httpUri = "http://" + rsocketUri.getHost() + ":" + (rsocketUri.getPort() - 1) + "/config/last/" + applicationName;
                try {
                    String confText = WebClient.create().get()
                            .uri(httpUri)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                            .retrieve()
                            .bodyToMono(String.class)
                            .block();
                    if (confText != null && !confText.isEmpty()) {
                        lastContent = confText;
                        PropertiesUtils.loadPropertiesContent(confs, lastContent);
                        this.confs = confs;
                        log.info("Succeed to receive config: ".concat(confs.toString()));
                    } else {
                        log.info(String.format("Failed to fetch config from RSocket Broker for app: '%s'", applicationName));
                    }
                    //标识app使用了配置中心
                    confs.setProperty(ConfigMetadataKeys.CONF, "true");
                    String autoRefreshKey = "kin.rsocket.conf.auto-refresh";
                    if ("true".equalsIgnoreCase(confs.getProperty(autoRefreshKey))) {
                        confs.setProperty(ConfigMetadataKeys.AUTO_REFRESH, "true");
                    }

                    this.source = new PropertiesPropertySource("kin-rsocket-broker", confs);
                    return source;
                } catch (Exception e) {
                    ExceptionUtils.throwExt(e);
                }
            }
        }

        String errorMsg = "Please setup spring.application.name, kin.rsocket.jwt-token and kin.rsocket.brokers in application.yml";
        log.error(errorMsg);
        throw new RuntimeException(errorMsg);
    }

    //getter && setter
    public String getLastContent() {
        return lastContent;
    }

    public void setLastContent(String lastContent) {
        this.lastContent = lastContent;
    }

    public Properties getConfs() {
        return confs;
    }
}
