package org.kin.rsocket.core.metadata;

import io.netty.buffer.ByteBuf;
import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.utils.JSON;

import java.util.HashSet;
import java.util.Set;

/**
 * service registry metadata: subscribed and published services from requester
 *
 * @author huangjianqin
 * @date 2021/3/25
 */
public class ServiceRegistryMetadata implements MetadataAware {
    /** published services */
    private Set<ServiceLocator> published = new HashSet<>();
    /** subscribed services */
    private Set<ServiceLocator> subscribed = new HashSet<>();

    public static ServiceRegistryMetadata of(ByteBuf content) {
        ServiceRegistryMetadata temp = new ServiceRegistryMetadata();
        temp.load(content);
        return temp;
    }

    /**
     * add published services
     */
    public void addPublishedService(ServiceLocator publishedService) {
        this.published.add(publishedService);
    }

    /**
     * add subscribed services
     */
    public void addSubscribedService(ServiceLocator subscribedService) {
        this.subscribed.add(subscribedService);
    }

    /**
     * @return 是否包含published services
     */
    public boolean containPublishedServices() {
        return published != null && !published.isEmpty();
    }

    @Override
    public RSocketMimeType mimeType() {
        return RSocketMimeType.ServiceRegistry;
    }

    @Override
    public ByteBuf getContent() {
        return JSON.writeByteBuf(this);
    }

    @Override
    public void load(ByteBuf byteBuf) {
        JSON.updateFieldValue(byteBuf, this);
    }

    //setter && getter
    public Set<ServiceLocator> getPublished() {
        return published;
    }

    public void setPublished(Set<ServiceLocator> published) {
        this.published = published;
    }

    public Set<ServiceLocator> getSubscribed() {
        return subscribed;
    }

    public void setSubscribed(Set<ServiceLocator> subscribed) {
        this.subscribed = subscribed;
    }
}
