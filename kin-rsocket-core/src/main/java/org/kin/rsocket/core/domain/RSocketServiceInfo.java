package org.kin.rsocket.core.domain;

import org.kin.framework.utils.CollectionUtils;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * @author huangjianqin
 * @date 2021/3/30
 */
public class RSocketServiceInfo implements Serializable {
    private static final long serialVersionUID = -2955933836182324398L;
    /** 空tags */
    private static final String[] EMPTY_TAGS = new String[0];

    /** package */
    private String namespace;
    /** interface name */
    private String name;
    /** service name */
    private String service;
    /** group */
    private String group;
    /** version */
    private String version;
    /** 服务描述 */
    private String description;
    /** 接口是否弃用 */
    private boolean deprecated;
    /** 方法信息 */
    private List<ReactiveMethodInfo> methods = Collections.emptyList();
    /** 服务标签 */
    private String[] tags = EMPTY_TAGS;

    //--------------------------------builder--------------------------------
    public static Builder builder() {
        return new Builder();
    }

    /** builder **/
    public static class Builder {
        private final RSocketServiceInfo reactiveServiceInfo = new RSocketServiceInfo();

        public Builder namespace(String namespace) {
            reactiveServiceInfo.namespace = namespace;
            return this;
        }

        public Builder name(String name) {
            reactiveServiceInfo.name = name;
            return this;
        }

        public Builder service(String service) {
            reactiveServiceInfo.service = service;
            return this;
        }

        public Builder group(String group) {
            reactiveServiceInfo.group = group;
            return this;
        }

        public Builder version(String version) {
            reactiveServiceInfo.version = version;
            return this;
        }

        public Builder description(String description) {
            reactiveServiceInfo.description = description;
            return this;
        }

        public Builder deprecated(boolean deprecated) {
            reactiveServiceInfo.deprecated = deprecated;
            return this;
        }

        public Builder methods(List<ReactiveMethodInfo> operations) {
            reactiveServiceInfo.methods = operations;
            return this;
        }

        public Builder tags(String[] tags) {
            if (CollectionUtils.isNonEmpty(tags)) {
                reactiveServiceInfo.tags = tags;
            }
            return this;
        }

        public RSocketServiceInfo build() {
            return reactiveServiceInfo;
        }
    }

    //setter && getter
    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<ReactiveMethodInfo> getMethods() {
        return methods;
    }

    public void setMethods(List<ReactiveMethodInfo> methods) {
        this.methods = methods;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    public void setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
    }

    public String[] getTags() {
        return tags;
    }

    public void setTags(String[] tags) {
        this.tags = tags;
    }
}
