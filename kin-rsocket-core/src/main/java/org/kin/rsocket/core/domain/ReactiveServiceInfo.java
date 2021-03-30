package org.kin.rsocket.core.domain;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * @author huangjianqin
 * @date 2021/3/30
 */
public class ReactiveServiceInfo implements Serializable {
    private static final long serialVersionUID = -2955933836182324398L;
    /** package */
    private String namespace;
    /** interface name */
    private String name;
    /** service name */
    private String serviceName;
    /** group */
    private String group;
    /** version */
    private String version;
    /** 描述, todo 后续通过注解增加 */
    private String description;
    /** 接口是否弃用 */
    private boolean deprecated;
    /** 方法信息 */
    private List<ReactiveMethodInfo> operations = Collections.emptyList();

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

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
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

    public List<ReactiveMethodInfo> getOperations() {
        return operations;
    }

    public void setOperations(List<ReactiveMethodInfo> operations) {
        this.operations = operations;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    public void setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
    }
}
