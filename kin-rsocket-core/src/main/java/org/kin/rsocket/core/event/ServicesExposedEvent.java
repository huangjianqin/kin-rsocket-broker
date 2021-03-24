package org.kin.rsocket.core.event;

import org.kin.rsocket.core.RSocketAppContext;
import org.kin.rsocket.core.ServiceLocator;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * services exposed event: register service on routing table
 *
 * @author huangjianqin
 * @date 2021/3/23
 */
public class ServicesExposedEvent implements CloudEventSupport<ServicesExposedEvent> {
    private static final long serialVersionUID = 3937855844811039738L;
    /** app UUID */
    private String appId;
    /** exposed services */
    private Set<ServiceLocator> services = new HashSet<>();

    public static CloudEventData<ServicesExposedEvent> toCloudEvent(Collection<ServiceLocator> serviceLocators) {
        ServicesExposedEvent servicesExposedEvent = new ServicesExposedEvent();
        for (ServiceLocator serviceLocator : serviceLocators) {
            servicesExposedEvent.addService(serviceLocator);
        }
        servicesExposedEvent.setAppId(RSocketAppContext.ID);
        return RSocketCloudEventBuilder
                .builder(servicesExposedEvent)
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

    public void addService(ServiceLocator serviceLocator) {
        this.services.add(serviceLocator);
    }
}
