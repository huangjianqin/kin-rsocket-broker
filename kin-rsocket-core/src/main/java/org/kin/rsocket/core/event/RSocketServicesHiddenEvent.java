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
public final class RSocketServicesHiddenEvent implements CloudEventSupport {
    private static final long serialVersionUID = -5175763743094581006L;
    /** application id */
    private String appId;
    /** need hidden services */
    private Set<ServiceLocator> services = Collections.emptySet();

    public static RSocketServicesHiddenEvent of(Collection<ServiceLocator> serviceLocators) {
        RSocketServicesHiddenEvent event = new RSocketServicesHiddenEvent();
        event.appId = RSocketAppContext.ID;
        event.services = new HashSet<>(serviceLocators.size());
        event.services.addAll(serviceLocators);
        return event;
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