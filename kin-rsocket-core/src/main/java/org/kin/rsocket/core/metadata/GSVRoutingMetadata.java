package org.kin.rsocket.core.metadata;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.rsocket.metadata.RoutingMetadata;
import io.rsocket.metadata.TaggingMetadataCodec;
import org.kin.rsocket.core.RSocketMimeType;
import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.utils.MurmurHash3;
import org.kin.rsocket.core.utils.Separators;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * GSV routing metadata, format as tagging routing data
 *
 * @author huangjianqin
 * @date 2021/3/24
 */
public final class GSVRoutingMetadata implements MetadataAware {
    /** group: region, datacenter, virtual group in datacenter */
    private String group;
    /** service name */
    private String service;
    /** method name */
    private String handler;
    /** version */
    private String version;
    /** endpoint */
    private String endpoint;
    /** sticky session */
    private boolean sticky;
    /** service ID */
    private transient int serviceId;

    public static GSVRoutingMetadata of(String group, String service, String handler, String version) {
        return of(group, service, handler, version, "", false);
    }

    public static GSVRoutingMetadata of(String group, String service, String handler, String version, String endpoint, boolean sticky) {
        GSVRoutingMetadata inst = new GSVRoutingMetadata();
        inst.group = group;
        inst.service = service;
        inst.handler = handler;
        inst.version = version;
        inst.endpoint = endpoint;
        return inst;
    }

    /**
     * @param serviceHandlerKey service.handler
     */
    public static GSVRoutingMetadata of(String group, String serviceHandlerKey, String version) {
        String service = "";
        String handler = "";
        int methodSymbolPosition = serviceHandlerKey.lastIndexOf(Separators.SERVICE_HANDLER);
        if (methodSymbolPosition > 0) {
            service = serviceHandlerKey.substring(0, methodSymbolPosition);
            handler = serviceHandlerKey.substring(methodSymbolPosition + 1);
        } else {
            service = serviceHandlerKey;
        }
        return of(group, service, handler, version);
    }

    public static GSVRoutingMetadata of(ByteBuf content) {
        GSVRoutingMetadata metadata = new GSVRoutingMetadata();
        metadata.load(content);
        return metadata;
    }

    public static GSVRoutingMetadata of(String routingKey) {
        GSVRoutingMetadata metadata = new GSVRoutingMetadata();
        metadata.parseRoutingKey(routingKey);
        return metadata;
    }

    private GSVRoutingMetadata() {
    }

    @Override
    public RSocketMimeType mimeType() {
        return RSocketMimeType.Routing;
    }

    @Override
    public ByteBuf getContent() {
        List<String> tags = new ArrayList<>();
        tags.add(genRoutingKey());
        if (endpoint != null && !endpoint.isEmpty()) {
            tags.add("e=" + endpoint);
        }
        if (sticky) {
            tags.add("sticky=1");
        }
        //官方使用tag
        return TaggingMetadataCodec.createTaggingContent(PooledByteBufAllocator.DEFAULT, tags);
    }

    @Override
    public void load(ByteBuf byteBuf) {
        Iterator<String> iterator = new RoutingMetadata(byteBuf).iterator();
        //first tag is routing for service name or method
        if (iterator.hasNext()) {
            parseRoutingKey(iterator.next());
        }
        while (iterator.hasNext()) {
            parseTags(iterator.next());
        }
    }

    /**
     * 解析route key
     * group!service.method:version?tags
     */
    private void parseRoutingKey(String routingKey) {
        String temp = routingKey;
        String tags = null;
        if (routingKey.contains(Separators.SERVICE_DEF_TAGS)) {
            temp = routingKey.substring(0, routingKey.indexOf(Separators.SERVICE_DEF_TAGS));
            tags = routingKey.substring(routingKey.indexOf(Separators.SERVICE_DEF_TAGS) + 1);
        }
        //group parse
        int groupSymbolPosition = temp.indexOf(Separators.GROUP_SERVICE);
        if (groupSymbolPosition > 0) {
            this.group = temp.substring(0, groupSymbolPosition);
            temp = temp.substring(groupSymbolPosition + 1);
        }
        //version
        int versionSymbolPosition = temp.lastIndexOf(Separators.SERVICE_VERSION);
        if (versionSymbolPosition > 0) {
            this.version = temp.substring(versionSymbolPosition + 1);
            temp = temp.substring(0, versionSymbolPosition);
        }
        //service & method
        int methodSymbolPosition = temp.lastIndexOf(Separators.SERVICE_HANDLER);
        if (methodSymbolPosition > 0) {
            this.service = temp.substring(0, methodSymbolPosition);
            this.handler = temp.substring(methodSymbolPosition + 1);
        } else {
            this.service = temp;
        }
        if (tags != null) {
            String[] tagParts = tags.split(Separators.TAG);
            for (String tagPart : tagParts) {
                parseTags(tagPart);
            }
        }
    }

    /**
     * 解析tag
     * e=&sticky=1
     */
    private void parseTags(String tag) {
        if (tag.startsWith("e=")) {
            this.endpoint = tag.substring(2);
        } else if ("sticky=1".equalsIgnoreCase(tag)) {
            this.sticky = true;
        }
    }

    /**
     * 生成route key
     *
     * @return route key
     */
    public String genRoutingKey() {
        StringBuilder routingBuilder = new StringBuilder();
        //group
        if (group != null && !group.isEmpty()) {
            routingBuilder.append(group).append(Separators.GROUP_SERVICE);
        }
        //service
        routingBuilder.append(service);
        //method
        if (handler != null && !handler.isEmpty()) {
            routingBuilder.append(Separators.SERVICE_HANDLER).append(handler);
        }
        //version
        if (version != null && !version.isEmpty()) {
            routingBuilder.append(Separators.SERVICE_VERSION).append(version);
        }
        if (this.sticky || this.endpoint != null) {
            routingBuilder.append(Separators.SERVICE_DEF_TAGS);
            if (this.sticky) {
                routingBuilder.append("sticky=1").append(Separators.TAG);
            }
            if (this.endpoint != null) {
                routingBuilder.append("e=").append(endpoint).append(Separators.TAG);
            }
        }
        return routingBuilder.toString();
    }

    /**
     * 生成service id
     *
     * @return service id
     */
    public int serviceId() {
        if (serviceId <= 0) {
            serviceId = MurmurHash3.hash32(gsv());
        }
        return serviceId;
    }

    /**
     * 生成gsv标识
     *
     * @return gsv标识
     */
    public String gsv() {
        return ServiceLocator.gsv(group, service, version);
    }

    /**
     * 生成handler id
     *
     * @return handler id
     */
    public Integer handlerId() {
        return MurmurHash3.hash32(service + Separators.SERVICE_HANDLER + handler);
    }

    //getter
    public String getGroup() {
        return group;
    }

    public String getService() {
        return service;
    }

    public String getHandler() {
        return handler;
    }

    public String getVersion() {
        return version;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public boolean isSticky() {
        return sticky;
    }
}

