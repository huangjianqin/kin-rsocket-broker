package org.kin.rsocket.core.event;

import org.kin.rsocket.core.RSocketAppContext;
import org.kin.rsocket.core.ServiceLocator;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 包含指定app所暴露的所有service gsv cloud event
 *
 * @author huangjianqin
 * @date 2021/3/23
 */
public final class ServicesExposedEvent implements CloudEventSupport {
    private static final long serialVersionUID = 3937855844811039738L;
    /** app UUID */
    private String appId;
    /** exposed services */
    private Set<ServiceLocator> services = new HashSet<>();

    public static CloudEventData<ServicesExposedEvent> of(ServiceLocator... serviceLocators) {
        return of(Arrays.asList(serviceLocators));
    }

    public static CloudEventData<ServicesExposedEvent> of(Collection<ServiceLocator> serviceLocators) {
        ServicesExposedEvent servicesExposedEvent = new ServicesExposedEvent();
        servicesExposedEvent.services.addAll(serviceLocators);
        servicesExposedEvent.setAppId(RSocketAppContext.ID);
        return CloudEventBuilder
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

    @Override
    public String toString() {
        return "ServicesExposedEvent{" +
                "appId='" + appId + '\'' +
                ", services=" + services +
                '}';
    }
}
