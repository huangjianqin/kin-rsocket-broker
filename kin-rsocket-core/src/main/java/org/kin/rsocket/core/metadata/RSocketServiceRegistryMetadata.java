package org.kin.rsocket.core.metadata;

import io.netty.buffer.ByteBuf;
import org.kin.rsocket.core.RSocketMimeType;
import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.utils.JSON;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * service registry metadata: subscribed and published services from requester
 *
 * @author huangjianqin
 * @date 2021/3/25
 */
public final class RSocketServiceRegistryMetadata implements MetadataAware {
    /** published services */
    private Set<ServiceLocator> published = new HashSet<>();
    /** subscribed services */
    private Set<ServiceLocator> subscribed = new HashSet<>();

    public static RSocketServiceRegistryMetadata of(ByteBuf content) {
        RSocketServiceRegistryMetadata temp = new RSocketServiceRegistryMetadata();
        temp.load(content);
        return temp;
    }

    private RSocketServiceRegistryMetadata() {
    }

    /**
     * @return 是否包含published services
     */
    public boolean containPublishedServices() {
        return published != null && !published.isEmpty();
    }

    @Override
    public RSocketMimeType mimeType() {
        return RSocketMimeType.SERVICE_REGISTRY;
    }

    @Override
    public ByteBuf getContent() {
        return JSON.writeByteBuf(this);
    }

    @Override
    public void load(ByteBuf byteBuf) {
        JSON.updateFieldValue(byteBuf, this);
    }

    //----------------------------------------------------------------builder----------------------------------------------------------------
    public static Builder builder() {
        return new Builder();
    }

    /** builder **/
    public static class Builder {
        private RSocketServiceRegistryMetadata serviceRegistryMetadata = new RSocketServiceRegistryMetadata();

        public Builder addPublishedService(ServiceLocator publishedService) {
            serviceRegistryMetadata.published.add(publishedService);
            return this;
        }

        public Builder addPublishedServices(ServiceLocator... publishedServices) {
            return addPublishedServices(Arrays.asList(publishedServices));
        }

        public Builder addPublishedServices(Collection<ServiceLocator> publishedServices) {
            serviceRegistryMetadata.published.addAll(publishedServices);
            return this;
        }

        public Builder addSubscribedService(ServiceLocator subscribedService) {
            serviceRegistryMetadata.subscribed.add(subscribedService);
            return this;
        }

        public Builder addSubscribedServices(ServiceLocator... subscribedServices) {
            return addSubscribedServices(Arrays.asList(subscribedServices));
        }

        public Builder addSubscribedServices(Collection<ServiceLocator> subscribedServices) {
            serviceRegistryMetadata.subscribed.addAll(subscribedServices);
            return this;
        }

        public RSocketServiceRegistryMetadata build() {
            return serviceRegistryMetadata;
        }
    }


    //getter
    public Set<ServiceLocator> getPublished() {
        return published;
    }

    public Set<ServiceLocator> getSubscribed() {
        return subscribed;
    }
}
