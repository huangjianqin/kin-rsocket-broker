package org.kin.rsocket.core.event;

import org.kin.rsocket.core.RSocketAppContext;
import org.kin.rsocket.core.ServiceLocator;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * services hidden event: remove services from routing table
 *
 * @author huangjianqin
 * @date 2021/3/24
 */
public final class ServicesHiddenEvent implements CloudEventSupport {
    private static final long serialVersionUID = -5175763743094581006L;
    /** application id */
    private String appId;
    /** need hidden services */
    private Set<ServiceLocator> services = Collections.emptySet();

    public static CloudEventData<ServicesHiddenEvent> of(Collection<ServiceLocator> serviceLocators) {
        ServicesHiddenEvent inst = new ServicesHiddenEvent();
        inst.appId = RSocketAppContext.ID;
        inst.services = new HashSet<>(serviceLocators.size());
        inst.services.addAll(serviceLocators);
        return CloudEventBuilder
                .builder(inst)
                .build();
    }

    //setter && getter
    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public Set<ServiceLocator> getServices() {
        return services;
    }

    public void setServices(Set<ServiceLocator> services) {
        this.services = services;
    }

    @Override
    public String toString() {
        return "ServicesHiddenEvent{" +
                "appId='" + appId + '\'' +
                ", services=" + services +
                '}';
    }
}