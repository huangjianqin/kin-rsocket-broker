package org.kin.rsocket.cloud.conf.client;

import org.kin.framework.collection.Tuple;
import org.kin.framework.utils.CollectionUtils;
import org.kin.framework.utils.ExceptionUtils;
import org.kin.framework.utils.PropertiesUtils;
import org.kin.framework.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.util.*;

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
        /*
         * ContextRefresher.refresh时, 只会重新加载bootstrap.yml配置, 不包括application.yml, 所有下面是三个配置信息必须要在bootstrap.yml里面定义
         * 不然会locate不到property source
         * 可以通过定义spring.cloud.refresh.additionalPropertySourcesToRetain, 让copy environment时, 把指定配置的内容也copy过来
         *
         * 如果bootstrap.yml没有任何内容, 则会去加载application.yml, 但bootstrap.yml有任何内容, 则不会先加载application.yml
         * 因此仅仅有application.yml也可以remote fetch configs, 但不支持刷新
         */
        //没有下面三个字段信息, 无法从broker那获取到具体的配置信息
        String jwtToken = environment.getProperty("kin.rsocket.jwt-token");
        //broker rsocket url
        String brokers = environment.getProperty("kin.rsocket.brokers");
        String applicationName = environment.getProperty("spring.application.name");
        //broker web host and port
        String brokerWebHostPorts = environment.getProperty("kin.rsocket.brokerWebHostPorts");

        if (Objects.nonNull(this.source)) {
            return source;
        }

        //broker web host and port
        List<Tuple<String, Integer>> webHostPorts;
        if (StringUtils.isNotBlank(brokerWebHostPorts)) {
            //配置了web host and port
            webHostPorts = toBrokerWebHostPorts(brokerWebHostPorts);
        } else {
            webHostPorts = parseBrokerWebHostPorts(brokers);
        }

        if (jwtToken != null && CollectionUtils.isNonEmpty(webHostPorts) && applicationName != null) {
            Properties confs = new Properties();
            for (Tuple<String, Integer> webHostPort : webHostPorts) {
                //首次通过http请求获取
                String httpUri = "http://" + webHostPort.first() + ":" + webHostPort.second() + "/api/org.kin.rsocket.core.conf.ConfigurationService/get";
                try {
                    String confText = WebClient.create().post()
                            .uri(httpUri)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue("[\"" + applicationName + "\",\"application.properties\"]")
                            .retrieve()
                            .bodyToMono(String.class)
                            .block();
                    if (confText != null && !confText.isEmpty()) {
                        lastContent = confText;
                        PropertiesUtils.loadPropertiesContent(confs, lastContent);
                        this.confs = confs;
                        log.info("succeed to receive config: ".concat(confs.toString()));
                    } else {
                        log.warn(String.format("failed to fetch config from RSocket Broker for app: '%s'", applicationName));
                    }
                    //标识app使用了配置中心
                    confs.setProperty(ConfigMetadataKeys.CONF, "true");
                    //自动刷新配置的key
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

        String errorMsg = "please setup spring.application.name, kin.rsocket.jwt-token and kin.rsocket.brokers in bootstrap.yml";
        log.error(errorMsg);
        throw new RuntimeException(errorMsg);
    }

    /**
     * 从broker rsocket url解析出broker web host and port列表
     */
    private List<Tuple<String, Integer>> parseBrokerWebHostPorts(String brokers) {
        if (StringUtils.isBlank(brokers)) {
            return Collections.emptyList();
        }

        List<Tuple<String, Integer>> hostPorts = new ArrayList<>(4);
        for (String brokerUriStr : brokers.split(",")) {
            URI brokerUri = URI.create(brokerUriStr);

            hostPorts.add(new Tuple<>(brokerUri.getHost(), brokerUri.getPort() - 1));
        }
        return hostPorts;
    }

    /**
     * 将broker web host and port转化成host and port列表
     */
    private List<Tuple<String, Integer>> toBrokerWebHostPorts(String brokerWebHostPorts) {
        if (StringUtils.isBlank(brokerWebHostPorts)) {
            return Collections.emptyList();
        }

        List<Tuple<String, Integer>> hostPorts = new ArrayList<>(4);
        for (String brokerWebHostPortStr : brokerWebHostPorts.split(",")) {
            String[] splits = brokerWebHostPortStr.split(":");

            hostPorts.add(new Tuple<>(splits[0], Integer.parseInt(splits[1])));
        }
        return hostPorts;
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
